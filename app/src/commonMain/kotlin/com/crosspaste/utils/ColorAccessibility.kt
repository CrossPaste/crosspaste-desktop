package com.crosspaste.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

object ColorAccessibility {

    fun isDarkColor(color: Color): Boolean = color.luminance() < 0.5f

    /**
     * Get the best text color for readability on a given background.
     * Considers luminance and WCAG contrast ratios.
     */
    fun Color.getBestTextColor(): Color {
        val bgLuminance = this.luminance()

        val baseTextColor =
            if (bgLuminance > 0.5f) {
                Color.Black
            } else {
                Color.White
            }

        // For medium luminance backgrounds (0.4-0.6), extra handling is needed
        if (bgLuminance in 0.4f..0.6f) {
            val contrastWithBlack = calculateContrast(this, Color.Black)
            val contrastWithWhite = calculateContrast(this, Color.White)

            // WCAG AA requires a contrast ratio of at least 4.5:1
            val minContrast = 4.5f

            return when {
                contrastWithBlack >= minContrast && contrastWithWhite >= minContrast -> {
                    if (contrastWithBlack > contrastWithWhite) Color.Black else Color.White
                }
                contrastWithBlack >= minContrast -> Color.Black
                contrastWithWhite >= minContrast -> Color.White
                else -> {
                    getEnhancedContrastColor()
                }
            }
        }

        return baseTextColor
    }

    fun Color.lighten(factor: Float): Color =
        Color(
            red = (red + (1f - red) * factor).coerceIn(0f, 1f),
            green = (green + (1f - green) * factor).coerceIn(0f, 1f),
            blue = (blue + (1f - blue) * factor).coerceIn(0f, 1f),
            alpha = alpha,
        )

    /**
     * Calculate the contrast ratio between two colors.
     * Based on WCAG 2.0 standard.
     */
    private fun calculateContrast(
        color1: Color,
        color2: Color,
    ): Float {
        val lum1 = color1.luminance()
        val lum2 = color2.luminance()

        val brightest = max(lum1, lum2)
        val darkest = min(lum1, lum2)

        return (brightest + 0.05f) / (darkest + 0.05f)
    }

    private fun Color.getEnhancedContrastColor(): Color {
        val luminance = this.luminance()

        return if (luminance > 0.5f) {
            Color(0xFF1A1A1A)
        } else {
            Color(0xFFF5F5F5)
        }
    }
}
