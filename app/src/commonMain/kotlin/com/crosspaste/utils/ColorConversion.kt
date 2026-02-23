package com.crosspaste.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.roundToInt

object ColorConversion {

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
