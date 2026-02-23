package com.crosspaste.utils

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

fun getColorParser(): ColorParser = ColorParser

object ColorParser {

    // RGB constants
    private const val RGB_MIN: Int = 0
    private const val RGB_MAX: Int = 255
    private const val RGB_RANGE: Int = 256

    // HSL constants
    private const val HSL_RANGE: Int = 360

    // Percentage constants
    private const val PERCENTAGE_MIN: Int = 0
    private const val PERCENTAGE_MAX: Int = 100

    // Opacity constants
    private const val OPACITY_MIN: Float = 0f
    private const val OPACITY_MAX: Float = 1f

    // CSS Value constants
    private const val PREFIX_RGB = "rgb"
    private const val PREFIX_RGBA = "rgba"
    private const val PREFIX_HSL = "hsl"
    private const val PREFIX_HSLA = "hsla"
    private const val PREFIX_HEX = '#'
    private const val TRANSPARENT = "transparent"

    // Regex patterns
    private const val PATTERN_PART_VALUE = "((?:\\+|\\-)?(?:[0-9]*\\.[0-9]*|[0-9]+)%?)"
    private const val PATTERN_PART_PERCENTAGE = "((?:\\+|\\-)?(?:(?:[0-9]*\\.[0-9]*|[0-9]+)%|0))"
    private const val PATTERN_PART_OPACITY = "([0-9]*\\.[0-9]*|[0-9]+)"

    private val PATTERN_RGB =
        Regex(
            "^$PREFIX_RGB\\s*\\(\\s*$PATTERN_PART_VALUE\\s*,\\s*$PATTERN_PART_VALUE\\s*,\\s*$PATTERN_PART_VALUE\\s*\\)$",
        )

    private val PATTERN_RGBA =
        Regex(
            "^$PREFIX_RGBA\\s*\\(\\s*$PATTERN_PART_VALUE\\s*,\\s*$PATTERN_PART_VALUE\\s*,\\s*$PATTERN_PART_VALUE\\s*,\\s*$PATTERN_PART_OPACITY\\s*\\)$",
        )

    private val PATTERN_HSL =
        Regex(
            "^$PREFIX_HSL\\s*\\(\\s*$PATTERN_PART_VALUE\\s*,\\s*$PATTERN_PART_PERCENTAGE\\s*,\\s*$PATTERN_PART_PERCENTAGE\\s*\\)$",
        )

    private val PATTERN_HSLA =
        Regex(
            "^$PREFIX_HSLA\\s*\\(\\s*$PATTERN_PART_VALUE\\s*,\\s*$PATTERN_PART_PERCENTAGE\\s*,\\s*$PATTERN_PART_PERCENTAGE\\s*,\\s*$PATTERN_PART_OPACITY\\s*\\)$",
        )

    fun toColor(value: String): Color? {
        parseHexColor(value)?.let { color -> return color }

        if (isRGBColorValue(value)) {
            return getParsedRGBColorValue(value)
        } else if (isRGBAColorValue(value)) {
            return getParsedRGBAColorValue(value)
        } else if (isHSLColorValue(value)) {
            return getParsedHSLColorValue(value)
        } else if (isHSLAColorValue(value)) {
            return getParsedHSLAColorValue(value)
        } else if (CssColor.isCssColor(value)) {
            return CssColor.colorOf(value)
        } else if (value == TRANSPARENT) {
            return Color(0f, 0f, 0f, 0f)
        }
        return null
    }

    fun parseHexColor(hex: String): Color? {
        val cleanHex = hex.removePrefix("#")
        return when (cleanHex.length) {
            3 -> {
                val r = cleanHex[0].toString().repeat(2).toIntOrNull(16) ?: return null
                val g = cleanHex[1].toString().repeat(2).toIntOrNull(16) ?: return null
                val b = cleanHex[2].toString().repeat(2).toIntOrNull(16) ?: return null
                Color(r, g, b)
            }
            4 -> {
                val r = cleanHex[0].toString().repeat(2).toIntOrNull(16) ?: return null
                val g = cleanHex[1].toString().repeat(2).toIntOrNull(16) ?: return null
                val b = cleanHex[2].toString().repeat(2).toIntOrNull(16) ?: return null
                val a = cleanHex[3].toString().repeat(2).toIntOrNull(16) ?: return null
                Color(r, g, b, a)
            }
            6 -> {
                val r = cleanHex.take(2).toIntOrNull(16) ?: return null
                val g = cleanHex.substring(2, 4).toIntOrNull(16) ?: return null
                val b = cleanHex.substring(4, 6).toIntOrNull(16) ?: return null
                Color(r, g, b)
            }
            8 -> {
                val r = cleanHex.take(2).toIntOrNull(16) ?: return null
                val g = cleanHex.substring(2, 4).toIntOrNull(16) ?: return null
                val b = cleanHex.substring(4, 6).toIntOrNull(16) ?: return null
                val a = cleanHex.substring(6, 8).toIntOrNull(16) ?: return null
                Color(r, g, b, a)
            }
            else -> null
        }
    }

    fun hslToColor(
        h: Float,
        s: Float,
        l: Float,
    ): Color {
        if (s == 0f) {
            val v = (l * 255f).roundToInt()
            return Color(v, v, v)
        }
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q

        fun hue2rgb(
            pv: Float,
            qv: Float,
            t0: Float,
        ): Float {
            var t = t0
            if (t < 0f) t += 1f
            if (t > 1f) t -= 1f
            return when {
                t < 1f / 6f -> pv + (qv - pv) * 6f * t
                t < 1f / 2f -> qv
                t < 2f / 3f -> pv + (qv - pv) * (2f / 3f - t) * 6f
                else -> pv
            }
        }
        val r = (hue2rgb(p, q, h + 1f / 3f) * 255f).roundToInt()
        val g = (hue2rgb(p, q, h) * 255f).roundToInt()
        val b = (hue2rgb(p, q, h - 1f / 3f) * 255f).roundToInt()
        return Color(r, g, b)
    }

    private fun isRGBColorValue(value: String?): Boolean {
        val realValue = value?.trim()
        return !realValue.isNullOrEmpty() &&
            realValue.startsWith(PREFIX_RGB) &&
            PATTERN_RGB.matches(realValue)
    }

    private fun isRGBAColorValue(value: String): Boolean {
        val realValue = value.trim()
        return realValue.startsWith(PREFIX_RGBA) &&
            PATTERN_RGBA.matches(realValue)
    }

    private fun isHSLColorValue(value: String): Boolean {
        val realValue = value.trim()
        return realValue.startsWith(PREFIX_HSL) &&
            PATTERN_HSL.matches(realValue)
    }

    private fun isHSLAColorValue(value: String): Boolean {
        val realValue = value.trim()
        return realValue.startsWith(PREFIX_HSLA) &&
            PATTERN_HSLA.matches(realValue)
    }

    private fun mod(
        value: Float,
        mod: Int,
    ): Float {
        var positiveValue = value
        while (positiveValue < 0) {
            positiveValue += mod
        }
        return positiveValue % mod
    }

    private fun getOpacityToUse(opacity: Float): Float =
        when {
            opacity < OPACITY_MIN -> OPACITY_MIN
            opacity > OPACITY_MAX -> OPACITY_MAX
            else -> opacity
        }

    private fun getHSLHueValue(hslPart: Float): Float = mod(hslPart, HSL_RANGE)

    private fun getHSLPercentageValue(hslPart: Float): Float =
        when {
            hslPart < PERCENTAGE_MIN -> PERCENTAGE_MIN.toFloat()
            hslPart > PERCENTAGE_MAX -> PERCENTAGE_MAX.toFloat()
            else -> hslPart
        }

    private fun parseRgbComponent(part: String): Int {
        val s = part.trim()
        val value =
            if (s.endsWith("%")) {
                val pct = s.dropLast(1).toFloatOrNull() ?: 0f
                (pct / 100f * RGB_MAX).roundToInt()
            } else {
                s.toFloatOrNull()?.roundToInt() ?: 0
            }
        return value.coerceIn(RGB_MIN, RGB_MAX)
    }

    private fun parseOpacity(part: String): Float {
        val f = part.trim().toFloatOrNull() ?: 1f
        return getOpacityToUse(f)
    }

    private fun toColor(
        r: Int,
        g: Int,
        b: Int,
        a: Float = 1f,
    ): Color =
        Color(
            red = r.coerceIn(0, 255) / 255f,
            green = g.coerceIn(0, 255) / 255f,
            blue = b.coerceIn(0, 255) / 255f,
            alpha = getOpacityToUse(a),
        )

    private fun parseHue(part: String): Float {
        val s = part.trim()
        val raw =
            if (s.endsWith("%")) {
                (s.dropLast(1).toFloatOrNull() ?: 0f) / 100f * HSL_RANGE
            } else {
                s.toFloatOrNull() ?: 0f
            }
        return getHSLHueValue(raw)
    }

    private fun parsePct01(part: String): Float {
        val s = part.trim()
        val pct = if (s.endsWith("%")) s.dropLast(1).toFloatOrNull() ?: 0f else s.toFloatOrNull() ?: 0f
        return getHSLPercentageValue(pct).coerceIn(PERCENTAGE_MIN.toFloat(), PERCENTAGE_MAX.toFloat()) / 100f
    }

    private fun getParsedRGBColorValue(value: String?): Color? {
        val real = value?.trim() ?: return null
        if (!real.startsWith("rgb", ignoreCase = true)) return null
        val m = PATTERN_RGB.find(real) ?: return null
        val (rStr, gStr, bStr) = m.groupValues.let { Triple(it[1], it[2], it[3]) }
        val r = parseRgbComponent(rStr)
        val g = parseRgbComponent(gStr)
        val b = parseRgbComponent(bStr)
        return toColor(r, g, b)
    }

    private fun getParsedRGBAColorValue(value: String?): Color? {
        val real = value?.trim() ?: return null
        if (!real.startsWith("rgba", ignoreCase = true)) return null
        val m = PATTERN_RGBA.find(real) ?: return null
        val r = parseRgbComponent(m.groupValues[1])
        val g = parseRgbComponent(m.groupValues[2])
        val b = parseRgbComponent(m.groupValues[3])
        val a = parseOpacity(m.groupValues[4])
        return toColor(r, g, b, a)
    }

    private fun getParsedHSLColorValue(value: String?): Color? {
        val real = value?.trim() ?: return null
        if (!real.startsWith("hsl", ignoreCase = true)) return null
        val m = PATTERN_HSL.find(real) ?: return null
        val h = parseHue(m.groupValues[1])
        val s01 = parsePct01(m.groupValues[2])
        val l01 = parsePct01(m.groupValues[3])
        return hslToColor(h / 360f, s01, l01)
    }

    private fun getParsedHSLAColorValue(value: String?): Color? {
        val real = value?.trim() ?: return null
        if (!real.startsWith("hsla", ignoreCase = true)) return null
        val m = PATTERN_HSLA.find(real) ?: return null
        val h = parseHue(m.groupValues[1])
        val s01 = parsePct01(m.groupValues[2])
        val l01 = parsePct01(m.groupValues[3])
        val a = parseOpacity(m.groupValues[4])
        val color = hslToColor(h / 360f, s01, l01)
        return color.copy(alpha = a)
    }
}
