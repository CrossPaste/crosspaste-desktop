package com.crosspaste.utils

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.safety.Safelist

fun getHtmlUtils(): HtmlUtils = HtmlUtils

object HtmlUtils {

    private val codecsUtils = getCodecsUtils()

    private const val PREVIEW_MAX_LINES = 20
    private const val PREVIEW_CHARS_PER_LINE = 80

    private val BLOCK_TAGS =
        setOf(
            "address",
            "article",
            "aside",
            "blockquote",
            "br",
            "dd",
            "details",
            "div",
            "dl",
            "dt",
            "fieldset",
            "figcaption",
            "figure",
            "footer",
            "h1",
            "h2",
            "h3",
            "h4",
            "h5",
            "h6",
            "header",
            "hr",
            "li",
            "main",
            "nav",
            "ol",
            "p",
            "pre",
            "section",
            "summary",
            "table",
            "tbody",
            "tfoot",
            "thead",
            "tr",
            "ul",
        )

    fun dataUrl(html: String): String {
        val encodedContent = codecsUtils.base64Encode(html.encodeToByteArray())
        return "data:text/html;charset=UTF-8;base64,$encodedContent"
    }

    fun getHtmlText(html: String): String? =
        runCatching {
            val ksoupDoc: Document = Ksoup.parse(html)
            val outputSettings = Document.OutputSettings()
            outputSettings.prettyPrint(false)
            ksoupDoc.outputSettings(outputSettings)
            ksoupDoc.select("br").before("\\n")
            ksoupDoc.select("p").before("\\n")
            val str = ksoupDoc.html().replace("\\\\n".toRegex(), "\n")
            Ksoup.clean(str, Safelist.none(), "", outputSettings)
        }.getOrNull()

    /**
     * Strip declarations that crash richeditor + Compose Multiplatform when
     * rendered through `RichTextState.setHtml`. richeditor's CSS parser maps
     * `em`, `rem`, and `%` to `TextUnit.Em`, but Compose's
     * `ParagraphBuilder.textStyleToParagraphStyle` calls `Density.toPx` on
     * `TextIndent.firstLine`/`restLine`, which only accepts `Sp` and throws
     * "Only Sp can convert to Px" otherwise. Stripping `text-indent` from
     * inline styles is acceptable for our previews/editor — the lost
     * indentation is purely cosmetic and not worth a full CSS rewrite.
     */
    fun sanitizeForRichText(html: String): String {
        if (!html.contains("text-indent", ignoreCase = true)) return html
        return runCatching {
            val doc = Ksoup.parse(html)
            doc.select("[style]").forEach { el ->
                val original = el.attr("style")
                val sanitized = stripTextIndent(original)
                if (sanitized != original) {
                    if (sanitized.isEmpty()) {
                        el.removeAttr("style")
                    } else {
                        el.attr("style", sanitized)
                    }
                }
            }
            doc.outerHtml()
        }.getOrElse { html }
    }

    private fun stripTextIndent(style: String): String =
        style
            .split(';')
            .filter { decl ->
                val colonIndex = decl.indexOf(':')
                if (colonIndex < 0) return@filter decl.isNotBlank()
                // Match the exact property name to avoid stripping vendor
                // prefixes such as `mso-text-indent` from Word-pasted HTML.
                val property = decl.substring(0, colonIndex).trim().lowercase()
                property != "text-indent"
            }.joinToString("; ") { it.trim() }
            .trim()

    fun ensureHtmlCharsetUtf8(html: String): String =
        runCatching {
            val doc = Ksoup.parse(html)
            val head = doc.head()

            head.select("meta[charset]").remove()
            head.select("meta[http-equiv=Content-Type]").remove()
            head.select("meta[name=viewport]").remove()

            val metaTags =
                listOf(
                    doc.createElement("meta").attr("charset", "UTF-8"),
                    doc
                        .createElement("meta")
                        .attr("http-equiv", "Content-Type")
                        .attr("content", "text/html; charset=UTF-8"),
                )

            metaTags.reversed().forEach { meta ->
                head.prependChild(meta)
            }

            doc.html()
        }.getOrElse { html }

    fun truncateForPreview(
        html: String,
        maxLines: Int = PREVIEW_MAX_LINES,
        charsPerLine: Int = PREVIEW_CHARS_PER_LINE,
    ): String {
        val sanitized = sanitizeForRichText(html)
        if (sanitized.length < maxLines * charsPerLine) return sanitized

        return runCatching {
            val doc = Ksoup.parse(sanitized)
            val body = doc.body()

            body.select("script, style, noscript, template").remove()

            truncateElement(body, maxLines, charsPerLine)
            doc.outerHtml()
        }.getOrElse { sanitized }
    }

    /**
     * Recursively truncates DOM elements to fit within the given line budget.
     * Returns the estimated number of visual lines consumed.
     */
    private fun truncateElement(
        element: Element,
        remainingLines: Int,
        charsPerLine: Int,
    ): Int {
        if (remainingLines <= 0) {
            element.remove()
            return 0
        }

        val tag = element.tagName()
        val children = element.children().toList()
        val hasBlockChild = children.any { it.tagName() in BLOCK_TAGS }

        if (!hasBlockChild) {
            if (tag == "br" || tag == "hr") return 1

            val text = element.text()
            if (text.isEmpty()) return 0

            val estimatedLines = maxOf(1, (text.length + charsPerLine - 1) / charsPerLine)
            if (estimatedLines <= remainingLines) return estimatedLines

            val maxChars = remainingLines * charsPerLine
            element.childNodes().toList().forEach { it.remove() }
            element.appendText(text.take(maxChars))
            return remainingLines
        }

        var usedLines = 0
        val toRemove = mutableListOf<Element>()

        for (child in children) {
            if (usedLines >= remainingLines) {
                toRemove.add(child)
                continue
            }

            val childLines = truncateElement(child, remainingLines - usedLines, charsPerLine)
            usedLines += if (child.tagName() in BLOCK_TAGS) maxOf(1, childLines) else childLines
        }

        toRemove.forEach { it.remove() }
        return usedLines
    }
}
