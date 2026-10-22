package com.crosspaste.utils

import androidx.compose.ui.graphics.Color
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element

object HtmlColorUtils {

    private val colorParser = getColorParser()

    private val CSS_COMMENT_PATTERN = "/\\*.*?\\*/".toRegex(RegexOption.DOT_MATCHES_ALL)

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

    private val WIDTH_100_PATTERN = "width:\\s*100%".toRegex()
    private val MIN_HEIGHT_100VH_PATTERN = "min-height:\\s*100vh".toRegex()
    private val HEIGHT_100VH_PATTERN = "height:\\s*100vh".toRegex()

    private fun hasFullWidthStyle(element: Element): Boolean {
        val style = element.attr("style")
        return style.contains(WIDTH_100_PATTERN) ||
            style.contains(MIN_HEIGHT_100VH_PATTERN) ||
            style.contains(HEIGHT_100VH_PATTERN)
    }

    private fun getColorFromElement(element: Element): Color? {
        element
            .attr("style")
            .takeIf { it.isNotEmpty() }
            ?.let { parseBackgroundColorFromStyle(it) }
            ?.let { return it }

        return element
            .attr("bgcolor")
            .takeIf { it.isNotEmpty() }
            ?.let { parseColor(it) }
    }

    private fun parseBackgroundColorFromStyle(style: String): Color? {
        val declarations = parseDeclarations(style)
        // background-color wins over the background shorthand
        val value =
            declarations["background-color"]
                ?: declarations["background"]
                ?: return null
        return extractColorFromValue(value)
    }

    /**
     * Parses an inline style into lowercase property -> value. The first declaration of a
     * property wins; `;` inside parentheses or quotes (e.g. data: URLs) does not split.
     */
    private fun parseDeclarations(style: String): Map<String, String> =
        splitTopLevel(style.replace(CSS_COMMENT_PATTERN, " ")) { it == ';' }
            .mapNotNull { declaration ->
                val colon = declaration.indexOf(':')
                if (colon <= 0) return@mapNotNull null
                val property = declaration.take(colon).trim().lowercase()
                val value = declaration.substring(colon + 1).trim()
                if (property.isEmpty() || value.isEmpty()) null else property to value
            }.distinctBy { it.first }
            .toMap()

    /**
     * Returns the first color in a background value: a plain color token, a color
     * function, or the first color stop of a gradient. `url(...)` and other tokens are skipped.
     */
    private fun extractColorFromValue(value: String): Color? =
        // With multiple background layers, the color belongs to the last one
        splitTopLevel(value) { it == ',' }
            .asReversed()
            .firstNotNullOfOrNull { extractColorFromLayer(it) }

    private fun extractColorFromLayer(layer: String): Color? {
        for (token in splitTopLevel(layer) { it.isWhitespace() }) {
            val name = token.substringBefore('(').lowercase()
            if (name == "url") continue
            if (name.contains("gradient") && token.endsWith(")")) {
                val arguments = token.substring(token.indexOf('(') + 1, token.length - 1)
                splitTopLevel(arguments) { it == ',' }
                    .firstNotNullOfOrNull { extractColorFromLayer(it) }
                    ?.let { return it }
                continue
            }
            parseColor(token)?.let { return it }
        }
        return null
    }

    private fun parseColor(token: String): Color? {
        val value = token.trim().lowercase()
        if (value.isEmpty()) return null
        return if (value.startsWith('#')) {
            colorParser.parseHexColor(value)
        } else if (value.first().isLetter()) {
            colorParser.toColor(value)
        } else {
            null
        }
    }

    /**
     * Splits [text] on characters matching [isSeparator] that sit outside parentheses and
     * quotes, dropping blank parts. Unbalanced input is tolerated.
     */
    private fun splitTopLevel(
        text: String,
        isSeparator: (Char) -> Boolean,
    ): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var quote: Char? = null
        for (c in text) {
            when {
                quote != null -> if (c == quote) quote = null
                c == '"' || c == '\'' -> quote = c
                c == '(' -> depth++
                c == ')' -> if (depth > 0) depth--
                depth == 0 && isSeparator(c) -> {
                    current
                        .toString()
                        .trim()
                        .takeIf { it.isNotEmpty() }
                        ?.let { parts.add(it) }
                    current.clear()
                    continue
                }
            }
            current.append(c)
        }
        current
            .toString()
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.let { parts.add(it) }
        return parts
    }
}
