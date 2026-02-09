package com.crosspaste.utils

import androidx.compose.ui.graphics.Color
import com.crosspaste.utils.ColorUtils.hslToColor
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.helger.css.decl.CSSDeclaration
import com.helger.css.decl.CSSExpression
import com.helger.css.decl.CSSExpressionMemberFunction
import com.helger.css.decl.CSSExpressionMemberTermSimple
import com.helger.css.decl.CSSExpressionMemberTermURI
import com.helger.css.reader.CSSReader
import com.helger.css.reader.CSSReaderSettings
import com.helger.css.utils.CSSColorHelper.getParsedHSLAColorValue
import com.helger.css.utils.CSSColorHelper.getParsedHSLColorValue
import com.helger.css.utils.CSSColorHelper.getParsedRGBAColorValue
import com.helger.css.utils.CSSColorHelper.getParsedRGBColorValue
import com.helger.css.utils.CSSColorHelper.isColorValue
import com.helger.css.utils.ECSSColor

object HtmlColorUtils {

    private val cssReaderSettings = CSSReaderSettings()

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

        element
            .attr("bgcolor")
            .takeIf { it.isNotEmpty() }
            ?.let { return normalizeColor(it) }

        return null
    }

    /**
     * Parse any supported color format to Compose Color using ph-css.
     */
    fun normalizeColor(color: String): Color {
        val trimmedColor = color.trim()
        val lowercaseColor = trimmedColor.lowercase()

        // Try RGB parsing
        getParsedRGBColorValue(lowercaseColor)?.let { cssRgb ->
            return Color(
                red = parseColorValue(cssRgb.red),
                green = parseColorValue(cssRgb.green),
                blue = parseColorValue(cssRgb.blue),
            )
        }

        // Try RGBA parsing
        getParsedRGBAColorValue(lowercaseColor)?.let { cssRgba ->
            return Color(
                red = parseColorValue(cssRgba.red),
                green = parseColorValue(cssRgba.green),
                blue = parseColorValue(cssRgba.blue),
                alpha = (parseAlphaValue(cssRgba.opacity) * 255).toInt(),
            )
        }

        // Try HSL parsing
        getParsedHSLColorValue(lowercaseColor)?.let { cssHsl ->
            val hue = parseHueValue(cssHsl.hue) / 360f
            val saturation = parsePercentageValue(cssHsl.saturation) / 100f
            val lightness = parsePercentageValue(cssHsl.lightness) / 100f
            return hslToColor(hue, saturation, lightness)
        }

        // Try HSLA parsing
        getParsedHSLAColorValue(lowercaseColor)?.let { cssHsla ->
            val hue = parseHueValue(cssHsla.hue) / 360f
            val saturation = parsePercentageValue(cssHsla.saturation) / 100f
            val lightness = parsePercentageValue(cssHsla.lightness) / 100f
            val alpha = parseAlphaValue(cssHsla.opacity)
            return hslToColor(hue, saturation, lightness).copy(alpha = alpha)
        }

        // Handle hex colors
        if (trimmedColor.startsWith("#")) {
            return parseHexColor(trimmedColor) ?: Color.White
        }

        // Handle named colors using ECSSColor
        ECSSColor.getFromNameCaseInsensitiveOrNull(trimmedColor)?.let { cssColor ->
            return Color(
                red = cssColor.red,
                green = cssColor.green,
                blue = cssColor.blue,
            )
        }

        return Color.Transparent
    }

    private fun parseHexColor(hex: String): Color? {
        val cleanHex = hex.removePrefix("#")
        return when (cleanHex.length) {
            3 -> {
                // #RGB -> #RRGGBB
                val r = cleanHex[0].toString().repeat(2).toIntOrNull(16) ?: return null
                val g = cleanHex[1].toString().repeat(2).toIntOrNull(16) ?: return null
                val b = cleanHex[2].toString().repeat(2).toIntOrNull(16) ?: return null
                Color(r, g, b)
            }
            4 -> {
                // #RGBA -> #RRGGBBAA
                val r = cleanHex[0].toString().repeat(2).toIntOrNull(16) ?: return null
                val g = cleanHex[1].toString().repeat(2).toIntOrNull(16) ?: return null
                val b = cleanHex[2].toString().repeat(2).toIntOrNull(16) ?: return null
                val a = cleanHex[3].toString().repeat(2).toIntOrNull(16) ?: return null
                Color(r, g, b, a)
            }
            6 -> {
                // #RRGGBB
                val r = cleanHex.take(2).toIntOrNull(16) ?: return null
                val g = cleanHex.substring(2, 4).toIntOrNull(16) ?: return null
                val b = cleanHex.substring(4, 6).toIntOrNull(16) ?: return null
                Color(r, g, b)
            }
            8 -> {
                // #RRGGBBAA
                val r = cleanHex.take(2).toIntOrNull(16) ?: return null
                val g = cleanHex.substring(2, 4).toIntOrNull(16) ?: return null
                val b = cleanHex.substring(4, 6).toIntOrNull(16) ?: return null
                val a = cleanHex.substring(6, 8).toIntOrNull(16) ?: return null
                Color(r, g, b, a)
            }
            else -> null
        }
    }

    private fun parseColorValue(value: String): Int {
        val trimmed = value.trim()
        return if (trimmed.endsWith("%")) {
            val percentage = trimmed.removeSuffix("%").toFloatOrNull() ?: 0f
            ((percentage / 100f) * 255f).toInt().coerceIn(0, 255)
        } else {
            trimmed.toIntOrNull()?.coerceIn(0, 255) ?: 0
        }
    }

    private fun parseAlphaValue(value: String): Float {
        val trimmed = value.trim()
        return if (trimmed.endsWith("%")) {
            val percentage = trimmed.removeSuffix("%").toFloatOrNull() ?: 100f
            (percentage / 100f).coerceIn(0f, 1f)
        } else {
            trimmed.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
        }
    }

    private fun parseHueValue(value: String): Float {
        val trimmed = value.trim()
        val degrees =
            when {
                trimmed.endsWith("deg") -> trimmed.removeSuffix("deg").toFloatOrNull() ?: 0f
                trimmed.endsWith("rad") -> {
                    val radians = trimmed.removeSuffix("rad").toFloatOrNull() ?: 0f
                    Math.toDegrees(radians.toDouble()).toFloat()
                }
                trimmed.endsWith("turn") -> {
                    val turns = trimmed.removeSuffix("turn").toFloatOrNull() ?: 0f
                    turns * 360f
                }
                else -> trimmed.toFloatOrNull() ?: 0f
            }
        return (degrees % 360f + 360f) % 360f // Ensure positive
    }

    private fun parsePercentageValue(value: String): Float {
        val trimmed = value.trim().removeSuffix("%")
        return (trimmed.toFloatOrNull() ?: 0f).coerceIn(0f, 100f)
    }

    private fun parseBackgroundColorFromStyle(style: String): Color? {
        // Wrap inline style into a complete CSS rule for parsing
        val wrappedCss = ".dummy { $style }"

        return try {
            val cssDoc = CSSReader.readFromStringStream(wrappedCss, cssReaderSettings)

            cssDoc?.allStyleRules?.firstOrNull()?.let { rule ->
                // Find background-related declarations
                val declarations = rule.allDeclarations

                // Prioritize background-color
                declarations
                    .firstOrNull {
                        it.property.equals("background-color", ignoreCase = true)
                    }?.let { declaration ->
                        return extractColorFromDeclaration(declaration)
                    }

                // If no background-color, look for background
                declarations
                    .firstOrNull {
                        it.property.equals("background", ignoreCase = true)
                    }?.let { declaration ->
                        return extractColorFromDeclaration(declaration)
                    }
            }
            null
        } catch (_: Exception) {
            // Return null on parsing failure
            null
        }
    }

    /**
     * Extract color using ph-css's structured approach
     */
    private fun extractColorFromDeclaration(declaration: CSSDeclaration): Color? {
        val expression = declaration.expression

        // For background-color, there's usually only one color value
        if (declaration.property.equals("background-color", ignoreCase = true)) {
            return extractColorFromExpression(expression)
        }

        // For background shorthand, need to find the color part
        if (declaration.property.equals("background", ignoreCase = true)) {
            return extractColorFromBackgroundExpression(expression)
        }

        return null
    }

    /**
     * Extract color from CSS expression
     */
    private fun extractColorFromExpression(expression: CSSExpression): Color? {
        // Iterate through all members of the expression
        for (i in 0 until expression.memberCount) {
            when (val member = expression.getMemberAtIndex(i)) {
                is CSSExpressionMemberTermSimple -> {
                    // Simple values (like color names, hex values, etc.)
                    val value = member.value
                    if (isColorValue(value.lowercase())) {
                        return normalizeColor(value)
                    }
                }

                is CSSExpressionMemberFunction -> {
                    // Function values (like rgb(), rgba(), hsl(), etc.)
                    val colorFunction = member.asCSSString
                    if (isColorValue(colorFunction.lowercase())) {
                        return normalizeColor(colorFunction)
                    }
                }
            }
        }

        return null
    }

    /**
     * Extract color from background shorthand expression
     */
    private fun extractColorFromBackgroundExpression(expression: CSSExpression): Color? {
        // Background shorthand may contain multiple values
        for (i in 0 until expression.memberCount) {
            when (val member = expression.getMemberAtIndex(i)) {
                is CSSExpressionMemberTermSimple -> {
                    val value = member.value

                    // Check if it's a color value
                    if (isColorValue(value.lowercase())) {
                        return normalizeColor(value)
                    }
                }

                is CSSExpressionMemberFunction -> {
                    val functionName = member.functionName.lowercase()

                    // Skip url() function
                    if (functionName == "url") continue

                    // Handle color functions
                    val colorFunction = member.asCSSString
                    if (isColorValue(colorFunction.lowercase())) {
                        return normalizeColor(colorFunction)
                    }

                    // Handle gradient functions (get the first color)
                    if (functionName.contains("gradient")) {
                        return extractFirstColorFromGradient(member)
                    }
                }

                is CSSExpressionMemberTermURI -> {
                    // Skip URI (background images)
                    continue
                }
            }
        }

        return null
    }

    /**
     * Extract the first color from a gradient
     */
    private fun extractFirstColorFromGradient(function: CSSExpressionMemberFunction): Color? {
        val expression = function.expression

        if (expression == null || expression.memberCount == 0) return null

        // The first color in a gradient is usually the second parameter (first might be direction)
        for (i in 0 until expression.memberCount) {
            when (val member = expression.getMemberAtIndex(i)) {
                is CSSExpressionMemberTermSimple -> {
                    val value = member.value
                    if (isColorValue(value.lowercase())) {
                        return normalizeColor(value)
                    }
                }
                is CSSExpressionMemberFunction -> {
                    val colorFunction = member.asCSSString
                    if (isColorValue(colorFunction.lowercase())) {
                        return normalizeColor(colorFunction)
                    }
                }
                else -> continue
            }
        }

        return null
    }
}
