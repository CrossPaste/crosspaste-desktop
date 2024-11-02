package com.crosspaste.utils

import kotlin.math.roundToInt

actual fun getColorUtils(): ColorUtils {
    return DesktopColorUtils
}

object DesktopColorUtils : ColorUtils {
    // Common hex digits pattern
    private const val HEX_PATTERN = """[0-9A-Fa-f]"""

    // Regex patterns for different color formats
    private val HEX_6_PATTERN = """^#?(${HEX_PATTERN}{6})$""".toRegex()
    private val HEX_8_PATTERN = """^#?(${HEX_PATTERN}{8})$""".toRegex()
    private val RGB_PATTERN = """^rgb\s*\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*\)$""".toRegex()
    private val RGBA_PATTERN = """^rgba\s*\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(1|0|0?\.\d+|1\.0)\s*\)$""".toRegex()

    override fun tryCovertToColor(text: String): Long? {
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
}
