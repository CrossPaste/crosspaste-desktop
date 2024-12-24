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
fun ThemeBackground1() {
    val backgroundColor = MaterialTheme.colorScheme.surface

//    val rightColor =
//        if (MaterialTheme.colorScheme.isLight()) {
//            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
//        } else {
//            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.64f)
//        }
//
//    val leftColor =
//        if (MaterialTheme.colorScheme.isLight()) {
//            MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
//        } else {
//            MaterialTheme.colorScheme.secondary.copy(alpha = 0.64f)
//        }

    Canvas(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        val canvasWidth = size.width

//        drawRect(
//            brush =
//                Brush.radialGradient(
//                    colors =
//                        listOf(
//                            backgroundColor.copy(alpha = 0.0f),
//                            backgroundColor.copy(alpha = 0.72f),
//                            leftColor.copy(alpha = 0.12f),
//                            leftColor.copy(alpha = 0.88f),
//                            leftColor.copy(alpha = 0.88f),
//                            leftColor,
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
//                            backgroundColor.copy(alpha = 0.72f),
//                            rightColor.copy(alpha = 0.12f),
//                            rightColor.copy(alpha = 0.24f),
//                            rightColor.copy(alpha = 0.64f),
//                            rightColor,
//                        ),
//                    center = center.copy(x = 1.3f * canvasWidth, y = -0f),
//                    radius = 1800f,
//                ),
//        )
    }
}

@Composable
fun ThemeBackground2() {
    val colorScheme = MaterialTheme.colorScheme
    val backgroundColor = colorScheme.surface

    // 使用 primary 和 secondary 的变体来创建和谐的配色
    val accentColor1 = colorScheme.primary.copy(alpha = 0.15f)
    val accentColor2 = colorScheme.secondary.copy(alpha = 0.12f)
    val accentColor3 = colorScheme.tertiary.copy(alpha = 0.10f)

    Canvas(
        modifier =
            Modifier
                .fillMaxSize()
                .background(backgroundColor),
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // 主要背景渐变 - 使用线性渐变创造层次感
        drawRect(
            brush =
                Brush.linearGradient(
                    colors =
                        listOf(
                            backgroundColor,
                            backgroundColor.copy(alpha = 0.95f),
                            accentColor1,
                        ),
                    start = Offset(0f, 0f),
                    end = Offset(canvasWidth, canvasHeight),
                ),
        )

        // 左上角柔和光晕
        drawRect(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            accentColor2,
                            accentColor2.copy(alpha = 0.05f),
                            backgroundColor.copy(alpha = 0f),
                        ),
                    center = Offset(0f, 0f),
                    radius = canvasHeight * 0.7f,
                ),
        )

        // 右下角装饰性渐变
        drawRect(
            brush =
                Brush.linearGradient(
                    colors =
                        listOf(
                            accentColor3.copy(alpha = 0f),
                            accentColor3.copy(alpha = 0.08f),
                            accentColor3,
                        ),
                    start = Offset(canvasWidth * 0.5f, canvasHeight * 0.5f),
                    end = Offset(canvasWidth, canvasHeight),
                ),
        )
    }
}

@Composable
fun ThemeBackground3() {
    val colorScheme = MaterialTheme.colorScheme
    val backgroundColor = colorScheme.surface

    // 使用相同的色值来创造对称的渐变效果
    val accentColor = colorScheme.tertiary.copy(alpha = 0.25f)

    Canvas(
        modifier =
            Modifier
                .fillMaxSize()
                .background(backgroundColor),
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // 中间主渐变层 - 创造浅色区域
        drawRect(
            brush =
                Brush.linearGradient(
                    colors =
                        listOf(
                            backgroundColor.copy(alpha = 0.95f),
                            backgroundColor.copy(alpha = 0.85f),
                            backgroundColor.copy(alpha = 0.95f),
                        ),
                    start = Offset(canvasWidth * 0.2f, 0f),
                    end = Offset(canvasWidth * 0.8f, canvasHeight),
                ),
        )

        // 左上角渐变
        drawRect(
            brush =
                Brush.linearGradient(
                    colors =
                        listOf(
                            accentColor.copy(alpha = 0.35f),
                            accentColor.copy(alpha = 0.20f),
                            accentColor.copy(alpha = 0f),
                        ),
                    start = Offset(0f, 0f),
                    end = Offset(canvasWidth * 0.4f, canvasHeight * 0.4f),
                ),
        )

        // 右下角渐变
        drawRect(
            brush =
                Brush.linearGradient(
                    colors =
                        listOf(
                            accentColor.copy(alpha = 0f),
                            accentColor.copy(alpha = 0.20f),
                            accentColor.copy(alpha = 0.35f),
                        ),
                    start = Offset(canvasWidth * 0.6f, canvasHeight * 0.6f),
                    end = Offset(canvasWidth, canvasHeight),
                ),
        )
    }
}

@Composable
fun ThemeBackground() {
    val colorScheme = MaterialTheme.colorScheme
    val backgroundColor = colorScheme.surface

    // 增加 alpha 值使颜色更深
    val accentColor = colorScheme.secondaryContainer.copy(alpha = 0.45f) // 从 0.25f 增加到 0.45f

    Canvas(
        modifier =
            Modifier
                .fillMaxSize()
                .background(backgroundColor),
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // 中间主渐变层 - 稍微加深过渡色
        drawRect(
            brush =
                Brush.linearGradient(
                    colors =
                        listOf(
                            backgroundColor.copy(alpha = 0.90f),
                            backgroundColor.copy(alpha = 0.75f), // 从 0.85f 加深到 0.75f
                            backgroundColor.copy(alpha = 0.90f),
                        ),
                    start = Offset(canvasWidth * 0.2f, 0f),
                    end = Offset(canvasWidth * 0.8f, canvasHeight),
                ),
        )

        // 左上角渐变 - 加深色值
        drawRect(
            brush =
                Brush.linearGradient(
                    colors =
                        listOf(
                            accentColor.copy(alpha = 0.55f), // 从 0.35f 加深到 0.55f
                            accentColor.copy(alpha = 0.35f), // 从 0.20f 加深到 0.35f
                            accentColor.copy(alpha = 0f),
                        ),
                    start = Offset(0f, 0f),
                    end = Offset(canvasWidth * 0.4f, canvasHeight * 0.4f),
                ),
        )

        // 右下角渐变 - 加深色值
        drawRect(
            brush =
                Brush.linearGradient(
                    colors =
                        listOf(
                            accentColor.copy(alpha = 0f),
                            accentColor.copy(alpha = 0.35f), // 从 0.20f 加深到 0.35f
                            accentColor.copy(alpha = 0.55f), // 从 0.35f 加深到 0.55f
                        ),
                    start = Offset(canvasWidth * 0.6f, canvasHeight * 0.6f),
                    end = Offset(canvasWidth, canvasHeight),
                ),
        )
    }
}
