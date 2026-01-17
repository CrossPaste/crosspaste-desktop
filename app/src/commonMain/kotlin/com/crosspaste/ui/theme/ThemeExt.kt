package com.crosspaste.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.ktx.harmonize
import com.materialkolor.ktx.toneColor
import com.materialkolor.palettes.TonalPalette

enum class SemanticColorPolicy {
    Dynamic,
    FixedHue,
}

data class SemanticColorGroup(
    val color: Color,
    val onColor: Color,
    val container: Color,
    val onContainer: Color,
) {
    companion object {
        fun create(
            sourceColor: Color,
            primary: Color,
            isDark: Boolean,
            policy: SemanticColorPolicy,
            isWarning: Boolean = false,
        ): SemanticColorGroup {
            val seed =
                when (policy) {
                    SemanticColorPolicy.Dynamic -> sourceColor.harmonize(primary)
                    SemanticColorPolicy.FixedHue -> sourceColor
                }
            val palette = TonalPalette.fromInt(seed.toArgb())

            return if (isDark) {
                SemanticColorGroup(
                    color = palette.toneColor(80),
                    onColor = palette.toneColor(20),
                    container = palette.toneColor(30),
                    onContainer = palette.toneColor(90),
                )
            } else {
                if (isWarning) {
                    SemanticColorGroup(
                        color = palette.toneColor(80),
                        onColor = palette.toneColor(10),
                        container = palette.toneColor(90),
                        onContainer = palette.toneColor(10),
                    )
                } else {
                    SemanticColorGroup(
                        color = palette.toneColor(40),
                        onColor = palette.toneColor(100),
                        container = palette.toneColor(90),
                        onContainer = palette.toneColor(10),
                    )
                }
            }
        }
    }
}

data class ThemeExt(
    val success: SemanticColorGroup,
    val info: SemanticColorGroup,
    val neutral: SemanticColorGroup,
    val warning: SemanticColorGroup,
    val special: SemanticColorGroup,
) {
    companion object {
        private val COLOR_SUCCESS = Color(0xFF2E7D32)
        private val COLOR_INFO = Color(0xFF0288D1)
        private val COLOR_NEUTRAL = Color(0xFF747775)
        private val COLOR_WARNING = Color(0xFFFBC02D)
        private val COLOR_SPECIAL = Color(0xFF6750A4)

        fun buildThemeExt(
            primary: Color,
            isDark: Boolean,
        ): ThemeExt {
            fun createGroup(
                source: Color,
                policy: SemanticColorPolicy,
                isWarning: Boolean = false,
            ) = SemanticColorGroup.create(source, primary, isDark, policy, isWarning)

            return ThemeExt(
                success = createGroup(COLOR_SUCCESS, SemanticColorPolicy.Dynamic),
                info = createGroup(COLOR_INFO, SemanticColorPolicy.Dynamic),
                neutral = createGroup(COLOR_NEUTRAL, SemanticColorPolicy.Dynamic),
                warning = createGroup(COLOR_WARNING, SemanticColorPolicy.FixedHue, isWarning = true),
                special = createGroup(COLOR_SPECIAL, SemanticColorPolicy.Dynamic),
            )
        }
    }
}
