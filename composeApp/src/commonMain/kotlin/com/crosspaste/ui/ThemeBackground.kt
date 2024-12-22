package com.crosspaste.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.crosspaste.ui.theme.CrossPasteTheme.isLight

@Composable
fun ThemeBackground() {
    val backgroundColor = MaterialTheme.colorScheme.surface

    val rightColor =
        if (MaterialTheme.colorScheme.isLight()) {
            Color(0xFF492355).copy(alpha = 0.12f)
        } else {
            Color(0xFF492355).copy(alpha = 0.64f)
        }

    val leftColor =
        if (MaterialTheme.colorScheme.isLight()) {
            Color(0xFF78243F).copy(alpha = 0.12f)
        } else {
            Color(0xFF78243F).copy(alpha = 0.64f)
        }

    Canvas(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
//        val canvasWidth = size.width
//
//        drawRect(
//            brush =
//                Brush.radialGradient(
//                    colors =
//                        listOf(
//                            backgroundColor.copy(alpha = 0.0f),
//                            leftColor,
//                            Color(0xFFFFC0CB).copy(0.88f),
//                            Color(0xFFDC82C2).copy(0.88f),
//                            Color(0xFFDC82C2).copy(alpha = 0.12f),
//                        ),
//                    center = center.copy(x = -100f, y = 0f),
//                    radius = 1000f,
//                ),
//        )
//
//        drawRect(
//            brush =
//                Brush.radialGradient(
//                    colors =
//                        listOf(
//                            backgroundColor.copy(alpha = 0.0f),
//                            rightColor,
//                            Color(0xFFFFC0CB).copy(0.64f),
//                            Color(0xFFDC82C2).copy(0.24f),
//                            Color(0xFFDC82C2).copy(alpha = 0.12f),
//                        ),
//                    center = center.copy(x = 1.3f * canvasWidth, y = -0f),
//                    radius = 1800f,
//                ),
//        )
    }
}
