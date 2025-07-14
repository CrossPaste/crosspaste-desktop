package com.crosspaste.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

fun getColorUtils(): ColorUtils = ColorUtils

object ColorUtils {

    // Common hex digits pattern
    private const val HEX_PATTERN = """[0-9A-Fa-f]"""

    // Regex patterns for different color formats
    private val HEX_6_PATTERN = """^(?:#|0[xX])?(${HEX_PATTERN}{6})$""".toRegex()
    private val HEX_8_PATTERN = """^(?:#|0[xX])?(${HEX_PATTERN}{8})$""".toRegex()
    private val RGB_PATTERN = """^rgb\s*\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*\)$""".toRegex()
    private val RGBA_PATTERN =
        """^rgba\s*\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(1|0|0?\.\d+|1\.0)\s*\)$"""
            .toRegex()

    fun tryCovertToColor(text: String): Long? {
        // Quick check for common patterns to fail fast
        if (text.isEmpty()) return null

        return when {
            text.startsWith("rgba(") -> tryConvertRGBA(text)
            text.startsWith("rgb(") -> tryConvertRGB(text)
            text.startsWith("#") || (text[0].isLetterOrDigit() && !text.startsWith("rgb")) -> tryConvertHex(text)
            else -> null
        }
    }

    private fun tryConvertHex(hex: String): Long? {
        // Try 8-digit hex first (includes alpha)
        HEX_8_PATTERN.find(hex)?.let { match ->
            return match.groupValues[1].toLongOrNull(16)
        }

        // Try 6-digit hex
        HEX_6_PATTERN.find(hex)?.let { match ->
            return "FF${match.groupValues[1]}".toLongOrNull(16)
        }

        return null
    }

    private fun tryConvertRGB(rgb: String): Long? {
        val match = RGB_PATTERN.find(rgb) ?: return null

        return runCatching {
            val (r, g, b) = match.destructured
            val red = r.toInt()
            val green = g.toInt()
            val blue = b.toInt()

            if (red !in 0..255 || green !in 0..255 || blue !in 0..255) {
                null
            } else {
                0xFF000000L or
                    (red.toLong() shl 16) or
                    (green.toLong() shl 8) or
                    blue.toLong()
            }
        }.getOrNull()
    }

    private fun tryConvertRGBA(rgba: String): Long? {
        val match = RGBA_PATTERN.find(rgba) ?: return null

        return runCatching {
            val (r, g, b, a) = match.destructured
            val red = r.toInt()
            val green = g.toInt()
            val blue = b.toInt()
            val alpha = (a.toFloat() * 255).roundToInt()

            if (red !in 0..255 || green !in 0..255 || blue !in 0..255 || alpha !in 0..255) {
                null
            } else {
                (alpha.toLong() shl 24) or
                    (red.toLong() shl 16) or
                    (green.toLong() shl 8) or
                    blue.toLong()
            }
        }.getOrNull()
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

    fun hslToColor(
        h: Float,
        s: Float,
        l: Float,
    ): Color {
        val h1 = h / 360f
        val s1 = s / 100f
        val l1 = l / 100f

        fun hueToRgb(
            p: Float,
            q: Float,
            t: Float,
        ): Float {
            var t1 = t
            if (t1 < 0f) t1 += 1f
            if (t1 > 1f) t1 -= 1f
            if (t1 < 1f / 6f) return p + (q - p) * 6f * t1
            if (t1 < 1f / 2f) return q
            if (t1 < 2f / 3f) return p + (q - p) * (2f / 3f - t1) * 6f
            return p
        }

        return if (s1 == 0f) {
            Color(l1, l1, l1)
        } else {
            val q = if (l1 < 0.5f) l1 * (1 + s1) else l1 + s1 - l1 * s1
            val p = 2 * l1 - q
            val r = hueToRgb(p, q, h1 + 1f / 3f)
            val g = hueToRgb(p, q, h1)
            val b = hueToRgb(p, q, h1 - 1f / 3f)
            Color(r, g, b)
        }
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
        val v = max

        return floatArrayOf(if (h < 0) h + 360 else h, s, v)
    }

    fun isNearWhiteOrBlack(
        r: Int,
        g: Int,
        b: Int,
    ): Boolean {
        val brightness = (r + g + b) / 3
        return brightness < 30 || brightness > 225
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

    /**
     * Determine whether the string represents a color value.
     */
    fun isColorValue(value: String): Boolean =
        value.matches(Regex("^#[0-9a-fA-F]{3,6}$")) ||
            value.matches(Regex("^rgb\\s*\\(.*\\)$", RegexOption.IGNORE_CASE)) ||
            value.matches(Regex("^rgba\\s*\\(.*\\)$", RegexOption.IGNORE_CASE)) ||
            isNamedColor(value)

    /**
     * Determine whether the string is a named color.
     */
    private fun isNamedColor(value: String): Boolean {
        val namedColors =
            setOf(
                "white",
                "black",
                "red",
                "green",
                "blue",
                "yellow",
                "cyan",
                "magenta",
                "silver",
                "gray",
                "maroon",
                "olive",
                "lime",
                "aqua",
                "teal",
                "navy",
                "fuchsia",
                "purple",
                "transparent",
            )
        return namedColors.contains(value.lowercase())
    }

    /**
     * Parse any supported color format to Compose Color.
     */
    fun normalizeColor(color: String): Color {
        val trimmedColor = color.trim()

        // Handle #RGB format
        if (trimmedColor.matches(Regex("^#[0-9a-fA-F]{3}$"))) {
            val r = trimmedColor[1].toString().repeat(2).toInt(16)
            val g = trimmedColor[2].toString().repeat(2).toInt(16)
            val b = trimmedColor[3].toString().repeat(2).toInt(16)
            return Color(r, g, b)
        }

        // Handle #RRGGBB format
        if (trimmedColor.matches(Regex("^#[0-9a-fA-F]{6}$"))) {
            val r = trimmedColor.substring(1, 3).toInt(16)
            val g = trimmedColor.substring(3, 5).toInt(16)
            val b = trimmedColor.substring(5, 7).toInt(16)
            return Color(r, g, b)
        }

        // Handle rgb() format
        if (trimmedColor.matches(
                Regex(
                    "^rgb\\s*\\(\\s*\\d+\\s*,\\s*\\d+\\s*,\\s*\\d+\\s*\\)$",
                    RegexOption.IGNORE_CASE,
                ),
            )
        ) {
            val values = trimmedColor.substringAfter("(").substringBefore(")").split(",")
            val r = values[0].trim().toInt()
            val g = values[1].trim().toInt()
            val b = values[2].trim().toInt()
            return Color(r, g, b)
        }

        // Handle rgba() format
        if (trimmedColor.matches(
                Regex(
                    "^rgba\\s*\\(\\s*\\d+\\s*,\\s*\\d+\\s*,\\s*\\d+\\s*,\\s*[\\d.]+\\s*\\)$",
                    RegexOption.IGNORE_CASE,
                ),
            )
        ) {
            val values = trimmedColor.substringAfter("(").substringBefore(")").split(",")
            val r = values[0].trim().toInt()
            val g = values[1].trim().toInt()
            val b = values[2].trim().toInt()
            val a = values[3].trim().toFloat()
            return Color(r, g, b, (a * 255).toInt())
        }

        // Handle named colors
        return when (trimmedColor.lowercase()) {
            "white" -> Color.White
            "black" -> Color.Black
            "red" -> Color.Red
            "green" -> Color(0xFF008000) // HTML green
            "blue" -> Color.Blue
            "yellow" -> Color.Yellow
            "cyan" -> Color.Cyan
            "magenta" -> Color.Magenta
            "silver" -> Color(0xFFC0C0C0)
            "gray", "grey" -> Color.Gray
            "maroon" -> Color(0xFF800000)
            "olive" -> Color(0xFF808000)
            "lime" -> Color(0xFF00FF00)
            "aqua" -> Color.Cyan
            "teal" -> Color(0xFF008080)
            "navy" -> Color(0xFF000080)
            "fuchsia" -> Color.Magenta
            "purple" -> Color(0xFF800080)
            "transparent" -> Color.White
            else -> Color.White
        }
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
}
