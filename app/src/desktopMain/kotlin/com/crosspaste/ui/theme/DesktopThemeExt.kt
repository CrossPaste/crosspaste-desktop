package com.crosspaste.ui.theme

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

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
