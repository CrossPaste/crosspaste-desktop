package com.crosspaste.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

fun getColorUtils(): ColorUtils {
    return ColorUtils
}

object ColorUtils {

    // Common hex digits pattern
    private const val HEX_PATTERN = """[0-9A-Fa-f]"""

    // Regex patterns for different color formats
    private val HEX_6_PATTERN = """^#?(${HEX_PATTERN}{6})$""".toRegex()
    private val HEX_8_PATTERN = """^#?(${HEX_PATTERN}{8})$""".toRegex()
    private val RGB_PATTERN = """^rgb\s*\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*\)$""".toRegex()
    private val RGBA_PATTERN = """^rgba\s*\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(1|0|0?\.\d+|1\.0)\s*\)$""".toRegex()

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

        return try {
            val (r, g, b) = match.destructured
            val red = r.toInt()
            val green = g.toInt()
            val blue = b.toInt()

            if (red !in 0..255 || green !in 0..255 || blue !in 0..255) {
                return null
            }

            0xFF000000L or
                (red.toLong() shl 16) or
                (green.toLong() shl 8) or
                blue.toLong()
        } catch (_: Exception) {
            null
        }
    }

    private fun tryConvertRGBA(rgba: String): Long? {
        val match = RGBA_PATTERN.find(rgba) ?: return null

        return try {
            val (r, g, b, a) = match.destructured
            val red = r.toInt()
            val green = g.toInt()
            val blue = b.toInt()
            val alpha = (a.toFloat() * 255).roundToInt()

            if (red !in 0..255 || green !in 0..255 || blue !in 0..255 || alpha !in 0..255) {
                return null
            }

            (alpha.toLong() shl 24) or
                (red.toLong() shl 16) or
                (green.toLong() shl 8) or
                blue.toLong()
        } catch (_: Exception) {
            null
        }
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

        if (s1 == 0f) {
            return Color(l1, l1, l1)
        } else {
            val q = if (l1 < 0.5f) l1 * (1 + s1) else l1 + s1 - l1 * s1
            val p = 2 * l1 - q
            val r = hueToRgb(p, q, h1 + 1f / 3f)
            val g = hueToRgb(p, q, h1)
            val b = hueToRgb(p, q, h1 - 1f / 3f)
            return Color(r, g, b)
        }
    }

    // 计算对比色
    fun getAdaptiveColor(
        backgroundColor: Color,
        targetHue: Float,
        baseSaturation: Float = 70f,
        baseLightness: Float = 60f,
    ): Color {
        val bgLuminance = backgroundColor.luminance()
        val bgHsl = backgroundColor.toHSL()

        // 根据背景亮度调整目标颜色的亮度
        val adjustedLightness =
            when {
                bgLuminance > 0.7f -> baseLightness - 20f // 背景很亮，降低颜色亮度
                bgLuminance < 0.3f -> baseLightness + 20f // 背景很暗，提高颜色亮度
                else -> baseLightness
            }

        // 根据背景饱和度调整目标颜色的饱和度
        val adjustedSaturation =
            when {
                bgHsl[1] > 70f -> baseSaturation - 20f // 背景饱和度高，降低颜色饱和度
                bgHsl[1] < 30f -> baseSaturation + 10f // 背景饱和度低，提高颜色饱和度
                else -> baseSaturation
            }

        return hslToColor(targetHue, adjustedSaturation, adjustedLightness)
    }
}
