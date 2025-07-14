package com.crosspaste.utils

import androidx.compose.ui.graphics.Color
import com.crosspaste.utils.ColorUtils.normalizeColor
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.safety.Safelist

fun getHtmlUtils(): HtmlUtils = HtmlUtils

object HtmlUtils {

    private val codecsUtils = getCodecsUtils()

    private val colorUtils = getColorUtils()

    fun dataUrl(html: String): String {
        val encodedContent = codecsUtils.base64Encode(html.encodeToByteArray())
        return "data:text/html;charset=UTF-8;base64,$encodedContent"
    }

    fun getHtmlText(html: String): String {
        val jsoupDoc: Document = Ksoup.parse(html)
        val outputSettings = Document.OutputSettings()
        outputSettings.prettyPrint(false)
        jsoupDoc.outputSettings(outputSettings)
        jsoupDoc.select("br").before("\\n")
        jsoupDoc.select("p").before("\\n")
        val str = jsoupDoc.html().replace("\\\\n".toRegex(), "\n")
        return Ksoup.clean(str, Safelist.none(), "", outputSettings)
    }

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

    fun getBackgroundColor(html: String): Color? {
        val document = Ksoup.parse(html)
        val body = document.body()

        getColorFromElement(body)?.let { return it }

        val directChildren =
            body
                .children()
                .filter { it.tagName() !in listOf("script", "style", "noscript") }

        when {
            directChildren.size == 1 -> {
                getColorFromElement(directChildren.first())?.let { return it }
            }

            directChildren.size > 1 -> {
                val mainContainer = findMainContainer(directChildren)
                mainContainer?.let { element ->
                    getColorFromElement(element)?.let { return it }
                }
            }
        }

        return body
            .parents()
            .firstNotNullOfOrNull { getColorFromElement(it) }
    }

    private fun findMainContainer(elements: List<Element>): Element? =
        elements.firstOrNull { element ->
            when {
                element.tagName() in listOf("main", "article") -> true
                element.id() in listOf("app", "root", "container", "wrapper", "main") -> true
                element.classNames().any { className ->
                    className in listOf("container", "wrapper", "main", "app", "root", "content")
                } -> true
                element.tagName() == "div" && hasFullWidthStyle(element) -> true
                else -> false
            }
        } ?: elements.firstOrNull { it.tagName() == "div" }

    private fun hasFullWidthStyle(element: Element): Boolean {
        val style = element.attr("style")
        return style.contains("width:\\s*100%".toRegex()) ||
            style.contains("min-height:\\s*100vh".toRegex()) ||
            style.contains("height:\\s*100vh".toRegex())
    }

    private fun getColorFromElement(element: Element): Color? {
        element
            .attr("style")
            .takeIf { it.isNotEmpty() }
            ?.let { extractBackgroundColorFromStyle(it) }
            ?.let { return it }

        element
            .attr("bgcolor")
            .takeIf { it.isNotEmpty() }
            ?.let { return normalizeColor(it) }

        return null
    }

    private fun extractBackgroundColorFromStyle(style: String): Color? {
        val patterns =
            listOf(
                "background-color" to Regex("background-color\\s*:\\s*([^;]+)", RegexOption.IGNORE_CASE),
                "background" to Regex("background\\s*:\\s*([^;]+)", RegexOption.IGNORE_CASE),
            )

        return patterns
            .firstNotNullOfOrNull { (_, pattern) ->
                pattern
                    .find(style)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
            }?.let { extractColorFromBackground(it) }
            ?.let { normalizeColor(it) }
    }

    private fun extractColorFromBackground(backgroundValue: String): String? =
        if (colorUtils.isColorValue(backgroundValue)) {
            backgroundValue
        } else {
            null
        }
}
