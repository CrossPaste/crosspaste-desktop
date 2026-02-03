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
