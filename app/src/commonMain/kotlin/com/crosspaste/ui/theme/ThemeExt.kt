package com.crosspaste.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.ktx.harmonize
import com.materialkolor.ktx.toneColor
import com.materialkolor.palettes.TonalPalette

data class ThemeExt(
    val success: SemanticColorGroup,
    val info: SemanticColorGroup,
    val neutral: SemanticColorGroup,
    val warning: SemanticColorGroup,
    val special: SemanticColorGroup,
) {
    companion object {
        fun buildThemeExt(
            primary: Color,
            isDark: Boolean,
        ): ThemeExt =
            ThemeExt(
                // Success: Standard Green
                success = SemanticColorGroup.createSemanticGroup(Color(0xFF2E7D32), primary, isDark),
                // Info: Standard Blue
                info = SemanticColorGroup.createSemanticGroup(Color(0xFF0288D1), primary, isDark),
                // Neutral: Standard Gray/Slate
                neutral = SemanticColorGroup.createSemanticGroup(Color(0xFF607D8B), primary, isDark),
                // Warning: Amber/Yellow (For Incompatible)
                warning = SemanticColorGroup.createSemanticGroup(Color(0xFFFBC02D), primary, isDark),
                // Special: Purple/Indigo (For Unmatched/Unverified)
                special = SemanticColorGroup.createSemanticGroup(Color(0xFF6750A4), primary, isDark),
            )
    }
}

data class SemanticColorGroup(
    val container: Color,
    val onContainer: Color,
) {
    companion object {
        fun createSemanticGroup(
            sourceColor: Color,
            primary: Color,
            isDark: Boolean,
        ): SemanticColorGroup {
            val harmonizedSeed = sourceColor.harmonize(primary)
            val palette = TonalPalette.fromInt(harmonizedSeed.toArgb())
            return if (!isDark) {
                SemanticColorGroup(
                    container = palette.toneColor(90),
                    onContainer = palette.toneColor(10),
                )
            } else {
                SemanticColorGroup(
                    container = palette.toneColor(30),
                    onContainer = palette.toneColor(90),
                )
            }
        }
    }
}
