package com.crosspaste.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush

@Composable
fun ThemeBackground() {
    val colorScheme = MaterialTheme.colorScheme
    val backgroundColor = colorScheme.surface

    val accentColor = colorScheme.secondaryContainer.copy(alpha = 0.45f)

    Canvas(
        modifier =
            Modifier
                .fillMaxSize()
                .background(backgroundColor),
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        drawRect(
            brush =
                Brush.linearGradient(
                    colors =
                        listOf(
                            backgroundColor.copy(alpha = 0.90f),
                            backgroundColor.copy(alpha = 0.75f),
                            backgroundColor.copy(alpha = 0.90f),
                        ),
                    start = Offset(canvasWidth * 0.2f, 0f),
                    end = Offset(canvasWidth * 0.8f, canvasHeight),
                ),
        )

        drawRect(
            brush =
                Brush.linearGradient(
                    colors =
                        listOf(
                            accentColor.copy(alpha = 0.55f),
                            accentColor.copy(alpha = 0.35f),
                            accentColor.copy(alpha = 0f),
                        ),
                    start = Offset(0f, 0f),
                    end = Offset(canvasWidth * 0.4f, canvasHeight * 0.4f),
                ),
        )

        drawRect(
            brush =
                Brush.linearGradient(
                    colors =
                        listOf(
                            accentColor.copy(alpha = 0f),
                            accentColor.copy(alpha = 0.35f),
                            accentColor.copy(alpha = 0.55f),
                        ),
                    start = Offset(canvasWidth * 0.6f, canvasHeight * 0.6f),
                    end = Offset(canvasWidth, canvasHeight),
                ),
        )
    }
}
