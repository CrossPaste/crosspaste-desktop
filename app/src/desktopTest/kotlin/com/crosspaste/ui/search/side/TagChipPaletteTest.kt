package com.crosspaste.ui.search.side

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.crosspaste.paste.PasteTag
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the tag chip palette. The tinted fills are only safe as long as the label and the
 * delete affordance stay readable on them, and the fills must stay opaque so a chip cannot
 * pick up the window material behind it.
 */
class TagChipPaletteTest {
    @Test
    fun `label keeps text contrast on the rest and selected fills`() {
        forEachSwatch { label, _, palette ->
            assertContrastAtLeast(label, "label on rest fill", palette.content, palette.container)
            assertContrastAtLeast(label, "label on selected fill", palette.content, palette.selectedContainer)
        }
    }

    @Test
    fun `delete icon keeps the non-text contrast minimum`() {
        forEachSwatch { label, _, palette ->
            val rendered = palette.closeIcon.compositeOver(palette.container)
            assertContrastAtLeast(
                label = label,
                subject = "delete icon on rest fill",
                foreground = rendered,
                background = palette.container,
                minimum = MIN_NON_TEXT_CONTRAST,
            )
        }
    }

    @Test
    fun `fills are opaque so the chip does not pick up the window material`() {
        forEachSwatch { label, _, palette ->
            assertEquals(1f, palette.container.alpha, "$label: rest fill must be opaque")
            assertEquals(1f, palette.selectedContainer.alpha, "$label: selected fill must be opaque")
        }
    }

    @Test
    fun `selected fill is stronger than the rest fill`() {
        forEachSwatch { label, isDark, palette ->
            val rest = relativeLuminance(palette.container)
            val selected = relativeLuminance(palette.selectedContainer)
            if (isDark) {
                assertTrue(selected > rest, "$label: selected fill should be lighter than the rest fill")
            } else {
                assertTrue(selected < rest, "$label: selected fill should be darker than the rest fill")
            }
        }
    }

    private fun forEachSwatch(action: (label: String, isDark: Boolean, palette: TagChipPalette) -> Unit) {
        PasteTag.colors.forEach { tagColor ->
            listOf(false, true).forEach { isDark ->
                val palette =
                    tagChipPalette(
                        accent = Color(tagColor.toInt()),
                        isDark = isDark,
                        surface = if (isDark) DARK_SURFACE else LIGHT_SURFACE,
                    )
                action("#${tagColor.toString(16).uppercase()} ${if (isDark) "dark" else "light"}", isDark, palette)
            }
        }
    }

    private fun assertContrastAtLeast(
        label: String,
        subject: String,
        foreground: Color,
        background: Color,
        minimum: Float = MIN_TEXT_CONTRAST,
    ) {
        val contrast = contrastRatio(foreground, background)
        assertTrue(
            contrast >= minimum,
            "$label: $subject contrast is $contrast, expected >= $minimum",
        )
    }

    private fun contrastRatio(
        first: Color,
        second: Color,
    ): Float {
        val firstLuminance = relativeLuminance(first)
        val secondLuminance = relativeLuminance(second)
        return (max(firstLuminance, secondLuminance) + 0.05f) / (min(firstLuminance, secondLuminance) + 0.05f)
    }

    private fun relativeLuminance(color: Color): Float {
        fun linearize(channel: Float): Float =
            if (channel <= 0.04045f) {
                channel / 12.92f
            } else {
                ((channel + 0.055f) / 1.055f).pow(2.4f)
            }
        return 0.2126f * linearize(color.red) + 0.7152f * linearize(color.green) + 0.0722f * linearize(color.blue)
    }

    private companion object {
        private const val MIN_TEXT_CONTRAST = 4.5f
        private const val MIN_NON_TEXT_CONTRAST = 3f

        // CrossPasteColor.surfaceLight / surfaceDark
        private val LIGHT_SURFACE = Color(0xFFFFFFFF)
        private val DARK_SURFACE = Color(0xFF0A0A0A)
    }
}
