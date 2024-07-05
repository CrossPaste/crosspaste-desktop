package com.crosspaste.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun ThemeBackground() {
    val backgroundColor = MaterialTheme.colors.surface

    val rightColor =
        if (MaterialTheme.colors.isLight) {
            Color(0xFF492355).copy(alpha = 0.12f)
        } else {
            Color(0xFF492355).copy(alpha = 0.64f)
        }

    val leftColor =
        if (MaterialTheme.colors.isLight) {
            Color(0xFF78243F).copy(alpha = 0.12f)
        } else {
            Color(0xFF78243F).copy(alpha = 0.64f)
        }

    Canvas(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        val canvasWidth = size.width

        drawRect(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            backgroundColor.copy(alpha = 0.0f),
                            leftColor,
                            Color(0xFFFFC0CB).copy(0.88f),
                            Color(0xAADC82C2).copy(0.88f),
                            backgroundColor.copy(alpha = 0.0f),
                        ),
                    center = center.copy(x = -100f, y = 0f),
                    radius = 1000f,
                ),
        )

        drawRect(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            backgroundColor.copy(alpha = 0.0f),
                            rightColor,
                            Color(0xFFFFC0CB).copy(0.64f),
                            Color(0xFFDC82C2).copy(0.24f),
                            backgroundColor.copy(alpha = 0.0f),
                        ),
                    center = center.copy(x = 1.3f * canvasWidth, y = -0f),
                    radius = 1800f,
                ),
        )
    }
}
