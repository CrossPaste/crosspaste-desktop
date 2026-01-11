package com.crosspaste.ui.theme

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
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
        private val COLOR_NEUTRAL = Color(0xFF607D8B)
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

// region Previews & UI Components

@Composable
private fun SemanticColorRow(
    name: String,
    group: SemanticColorGroup,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth().height(56.dp)) {
        // color emphasis
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().background(group.color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$name / color",
                color = group.onColor,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        // container
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight().background(group.container),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$name / container",
                color = group.onContainer,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun SemanticColorsPreviewContent(
    primary: Color,
    isDark: Boolean,
) {
    val ext = ThemeExt.buildThemeExt(primary = primary, isDark = isDark)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SemanticColorRow("Success", ext.success)
        SemanticColorRow("Info", ext.info)
        SemanticColorRow("Warning", ext.warning)
        SemanticColorRow("Neutral", ext.neutral)
        SemanticColorRow("Special", ext.special)
    }
}

@Preview
@Composable
private fun SemanticColorsLightPreview() {
    val lightScheme = SeaColor.lightColorScheme
    MaterialTheme(colorScheme = lightScheme) {
        SemanticColorsPreviewContent(primary = lightScheme.primary, isDark = false)
    }
}

@Preview
@Composable
private fun SemanticColorsDarkPreview() {
    val darkScheme = SeaColor.darkColorScheme
    MaterialTheme(colorScheme = darkScheme) {
        SemanticColorsPreviewContent(primary = darkScheme.primary, isDark = true)
    }
}
