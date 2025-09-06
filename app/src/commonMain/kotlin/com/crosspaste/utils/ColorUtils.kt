package com.crosspaste.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

fun getColorUtils(): ColorUtils = ColorUtils

object ColorUtils {

    /** RGB minimum value */
    const val RGB_MIN: Int = 0

    /** RGB maximum value */
    const val RGB_MAX: Int = 255

    /** RGB range (max-min+1) */
    const val RGB_RANGE: Int = 256

    /** HSL minimum value (for Hue only) */
    const val HSL_MIN: Int = 0

    /** HSL maximum value (for Hue only) */
    const val HSL_MAX: Int = 359

    /** HSL range (max-min+1) (for Hue only) */
    const val HSL_RANGE: Int = 360

    /** Percentage minimum value (for HSL) */
    const val PERCENTAGE_MIN: Int = 0

    /** Percentage maximum value (for HSL) */
    const val PERCENTAGE_MAX: Int = 100

    /** Minimum opacity value */
    const val OPACITY_MIN: Float = 0f

    /** Maximum opacity value */
    const val OPACITY_MAX: Float = 1f

    // CSS Value constants
    private const val PREFIX_RGB = "rgb"
    private const val PREFIX_RGBA = "rgba"
    private const val PREFIX_HSL = "hsl"
    private const val PREFIX_HSLA = "hsla"
    private const val PREFIX_HEX = '#'
    private const val PREFIX_HSL_OPEN = "hsl("

    private const val SUFFIX_HSL_CLOSE = ")"
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

    private val PATTERN_HEX = Regex("^$PREFIX_HEX([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")

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

    /**
     * Check if the passed String is valid CSS RGB color value.
     * Example value: rgb(255,0,0)
     */
    fun isRGBColorValue(value: String?): Boolean {
        val realValue = value?.trim()
        return !realValue.isNullOrEmpty() &&
            realValue.startsWith(PREFIX_RGB) &&
            PATTERN_RGB.matches(realValue)
    }

    /**
     * Check if the passed String is valid CSS RGBA color value.
     * Example value: rgba(255,0,0, 0.1)
     */
    fun isRGBAColorValue(value: String): Boolean {
        val realValue = value.trim()
        return realValue.startsWith(PREFIX_RGBA) &&
            PATTERN_RGBA.matches(realValue)
    }

    /**
     * Check if the passed String is valid CSS HSL color value.
     * Example value: hsl(255,0%,0%)
     */
    fun isHSLColorValue(value: String): Boolean {
        val realValue = value.trim()
        return realValue.startsWith(PREFIX_HSL) &&
            PATTERN_HSL.matches(realValue)
    }

    /**
     * Check if the passed String is valid CSS HSLA color value.
     * Example value: hsla(255,0%,0%, 0.1)
     */
    fun isHSLAColorValue(value: String): Boolean {
        val realValue = value.trim()
        return realValue.startsWith(PREFIX_HSLA) &&
            PATTERN_HSLA.matches(realValue)
    }

    /**
     * Check if the passed String is valid CSS hex color value.
     * Example value: #ff0000
     */
    fun isHexColorValue(value: String): Boolean {
        val realValue = value.trim()
        return realValue[0] == PREFIX_HEX &&
            PATTERN_HEX.matches(realValue)
    }

    private fun mod(
        value: Int,
        mod: Int,
    ): Int {
        // modulo does not work as expected on negative numbers
        var positiveValue = value
        while (positiveValue < 0) {
            positiveValue += mod
        }
        return positiveValue % mod
    }

    private fun mod(
        value: Float,
        mod: Int,
    ): Float {
        // modulo does not work as expected on negative numbers
        var positiveValue = value
        while (positiveValue < 0) {
            positiveValue += mod
        }
        return positiveValue % mod
    }

    /**
     * Convert the passed value to a valid RGB value in the range 0-255.
     */
    fun getRGBValue(rgbPart: Int): Int = mod(rgbPart, RGB_RANGE)

    /**
     * Ensure that the passed opacity value is in the range OPACITY_MIN and OPACITY_MAX.
     */
    fun getOpacityToUse(opacity: Float): Float =
        when {
            opacity < OPACITY_MIN -> OPACITY_MIN
            opacity > OPACITY_MAX -> OPACITY_MAX
            else -> opacity
        }

    /**
     * Get the passed value as a valid HSL Hue value in the range of HSL_MIN-HSL_MAX
     */
    fun getHSLHueValue(hslPart: Int): Int = mod(hslPart, HSL_RANGE)

    /**
     * Get the passed value as a valid HSL Hue value in the range of HSL_MIN-HSL_MAX
     */
    fun getHSLHueValue(hslPart: Float): Float = mod(hslPart, HSL_RANGE)

    /**
     * Get the passed value as a valid HSL Saturation or Lightness value
     * in the range of PERCENTAGE_MIN-PERCENTAGE_MAX (percentage).
     */
    fun getHSLPercentageValue(hslPart: Int): Int =
        when {
            hslPart < PERCENTAGE_MIN -> PERCENTAGE_MIN
            hslPart > PERCENTAGE_MAX -> PERCENTAGE_MAX
            else -> hslPart
        }

    /**
     * Get the passed value as a valid HSL Saturation or Lightness value
     * in the range of PERCENTAGE_MIN-PERCENTAGE_MAX (percentage).
     */
    fun getHSLPercentageValue(hslPart: Float): Float =
        when {
            hslPart < PERCENTAGE_MIN -> PERCENTAGE_MIN.toFloat()
            hslPart > PERCENTAGE_MAX -> PERCENTAGE_MAX.toFloat()
            else -> hslPart
        }

    /**
     * Get the passed values as CSS HSL color value
     */
    fun getHSLColorValue(
        hue: Int,
        saturation: Int,
        lightness: Int,
    ): String =
        buildString {
            append(PREFIX_HSL_OPEN)
            append(getHSLHueValue(hue))
            append(',')
            append(getHSLPercentageValue(saturation))
            append("%,")
            append(getHSLPercentageValue(lightness))
            append("%")
            append(SUFFIX_HSL_CLOSE)
        }

    private fun getRGBPartAsHexString(rgbPart: Int): String {
        val value = getRGBValue(rgbPart)
        return value.toString(16).padStart(2, '0').uppercase()
    }

    fun getHexColorValue(
        red: Int,
        green: Int,
        blue: Int,
    ): String =
        buildString {
            append(PREFIX_HEX)
            append(getRGBPartAsHexString(red))
            append(getRGBPartAsHexString(green))
            append(getRGBPartAsHexString(blue))
        }

    /**
     * Get the passed RGB values as HSL values compliant for CSS in the CSS range (0-359, 0-100, 0-100)
     *
     * @return An array of 3 floats, containing hue, saturation and lightness (in this order).
     * The first value is in the range 0-359, and the remaining two values are in the range 0-100 (percentage).
     */
    fun getRGBAsHSLValue(
        red: Int,
        green: Int,
        blue: Int,
    ): FloatArray {
        // Convert RGB to HSB(=HSL) - brightness vs. lightness
        var cmax = if (red > green) red else green
        if (blue > cmax) cmax = blue

        var cmin = if (red < green) red else green
        if (blue < cmin) cmin = blue

        val brightness = cmax / 255.0f

        val saturation: Float =
            if (cmax != 0) {
                (cmax - cmin).toFloat() / cmax.toFloat()
            } else {
                0f
            }

        val hue: Float =
            if (saturation == 0f) {
                0f
            } else {
                val redc = (cmax - red).toFloat() / (cmax - cmin).toFloat()
                val greenc = (cmax - green).toFloat() / (cmax - cmin).toFloat()
                val bluec = (cmax - blue).toFloat() / (cmax - cmin).toFloat()

                var h =
                    when (cmax) {
                        red -> bluec - greenc
                        green -> 2.0f + redc - bluec
                        else -> 4.0f + greenc - redc
                    }
                h /= 6.0f
                if (h < 0) h += 1.0f
                h
            }

        return floatArrayOf(
            hue * HSL_MAX,
            saturation * PERCENTAGE_MAX,
            brightness * PERCENTAGE_MAX,
        )
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

    fun getParsedRGBColorValue(value: String?): Color? {
        val real = value?.trim() ?: return null
        if (!real.startsWith("rgb", ignoreCase = true)) return null
        val m = PATTERN_RGB.find(real) ?: return null
        val (rStr, gStr, bStr) = m.groupValues.let { Triple(it[1], it[2], it[3]) }
        val r = parseRgbComponent(rStr)
        val g = parseRgbComponent(gStr)
        val b = parseRgbComponent(bStr)
        return toColor(r, g, b)
    }

    fun getParsedRGBAColorValue(value: String?): Color? {
        val real = value?.trim() ?: return null
        if (!real.startsWith("rgba", ignoreCase = true)) return null
        val m = PATTERN_RGBA.find(real) ?: return null
        val r = parseRgbComponent(m.groupValues[1])
        val g = parseRgbComponent(m.groupValues[2])
        val b = parseRgbComponent(m.groupValues[3])
        val a = parseOpacity(m.groupValues[4])
        return toColor(r, g, b, a)
    }

    fun getParsedHSLColorValue(value: String?): Color? {
        val real = value?.trim() ?: return null
        if (!real.startsWith("hsl", ignoreCase = true)) return null
        val m = PATTERN_HSL.find(real) ?: return null
        val h = parseHue(m.groupValues[1]) // 0..360
        val s01 = parsePct01(m.groupValues[2]) // 0..1
        val l01 = parsePct01(m.groupValues[3]) // 0..1
        return hslToColor(h / 360f, s01, l01)
    }

    fun getParsedHSLAColorValue(value: String?): Color? {
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

    fun parseHexColor(hex: String): Color? {
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

    fun Color.toHSL(): FloatArray {
        val r: Float = red
        val g: Float = green
        val b: Float = blue

        val max = max(max(r, g), b)
        val min = min(min(r, g), b)
        var h: Float
        val s: Float
        val l = (max + min) / 2f

        if (max == min) {
            h = 0f
            s = 0f
        } else {
            val d = max - min
            s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
            h =
                when (max) {
                    r -> (g - b) / d + (if (g < b) 6f else 0f)
                    g -> (b - r) / d + 2f
                    else -> (r - g) / d + 4f
                }
            h /= 6f
        }

        return floatArrayOf(h * 360, s * 100, l * 100)
    }

    // Calculate adaptive color based on background
    fun getAdaptiveColor(
        backgroundColor: Color,
        targetHue: Float,
        baseSaturation: Float = 70f,
        baseLightness: Float = 60f,
    ): Color {
        val bgLuminance = backgroundColor.luminance()
        val bgHsl = backgroundColor.toHSL()

        // Adjust target color lightness based on background luminance
        val adjustedLightness =
            when {
                bgLuminance > 0.7f -> baseLightness - 20f // Bright background, decrease color lightness
                bgLuminance < 0.3f -> baseLightness + 20f // Dark background, increase color lightness
                else -> baseLightness
            }

        // Adjust target color saturation based on background saturation
        val adjustedSaturation =
            when {
                bgHsl[1] > 70f -> baseSaturation - 20f // High background saturation, decrease color saturation
                bgHsl[1] < 30f -> baseSaturation + 10f // Low background saturation, increase color saturation
                else -> baseSaturation
            }

        return hslToColor(targetHue, adjustedSaturation, adjustedLightness)
    }

    fun rgbToHsv(
        r: Int,
        g: Int,
        b: Int,
    ): FloatArray {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f

        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val delta = max - min

        val h =
            when {
                delta == 0f -> 0f
                max == rf -> ((gf - bf) / delta) % 6
                max == gf -> (bf - rf) / delta + 2
                else -> (rf - gf) / delta + 4
            } * 60

        val s = if (max == 0f) 0f else delta / max

        return floatArrayOf(if (h < 0) h + 360 else h, s, max)
    }

    fun isNearWhiteOrBlack(
        r: Int,
        g: Int,
        b: Int,
    ): Boolean {
        val brightness = (r + g + b) / 3
        return brightness !in 30..225
    }

    fun hsvToRgb(
        h: Float,
        s: Float,
        v: Float,
    ): IntArray {
        val c = v * s
        val x = c * (1 - abs((h / 60) % 2 - 1))
        val m = v - c

        val (r, g, b) =
            when ((h / 60).toInt()) {
                0 -> Triple(c, x, 0f)
                1 -> Triple(x, c, 0f)
                2 -> Triple(0f, c, x)
                3 -> Triple(0f, x, c)
                4 -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }

        return intArrayOf(
            ((r + m) * 255).toInt(),
            ((g + m) * 255).toInt(),
            ((b + m) * 255).toInt(),
        )
    }

    fun isDarkColor(color: Color): Boolean {
        // Use luminance to determine if the color is dark
        return color.luminance() < 0.5f
    }

    fun Color.toHexString(): String {
        val alpha = (alpha * 255).toInt()
        val red = (red * 255).toInt()
        val green = (green * 255).toInt()
        val blue = (blue * 255).toInt()

        return "0x${alpha.toString(16).padStart(2, '0').uppercase()}" +
            red.toString(16).padStart(2, '0').uppercase() +
            green.toString(16).padStart(2, '0').uppercase() +
            blue.toString(16).padStart(2, '0').uppercase()
    }

    fun Color.toRGBString(): String {
        val argb = this.toArgb()

        val alpha = ((argb shr 24) and 0xFF) / 255.0f
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF

        val alphaFormatted = ((alpha * 100).roundToInt()) / 100.0

        return "RGBA($red, $green, $blue, $alphaFormatted)"
    }
}
