package com.crosspaste.ui.base

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SketchBackground(
    width: Dp,
    height: Dp,
    cornerRadius: Dp,
    color: Color,
) {
    Canvas(modifier = Modifier.size(width, height)) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val cornerRadiusPx = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())

        val roundRect =
            RoundRect(
                left = 0f,
                top = 0f,
                right = canvasWidth,
                bottom = canvasHeight,
                topLeftCornerRadius = cornerRadiusPx,
                topRightCornerRadius = cornerRadiusPx,
                bottomRightCornerRadius = cornerRadiusPx,
                bottomLeftCornerRadius = cornerRadiusPx,
            )

        val clipPath =
            Path().apply {
                addRoundRect(roundRect)
            }

        clipPath(clipPath) {
            drawRoundRect(
                color = color,
                size = size,
                cornerRadius = cornerRadiusPx,
                style = Stroke(width = 2.dp.toPx()),
            )

            for (x in 0..(canvasWidth.toInt() * 2) step 10) {
                drawLine(
                    start = androidx.compose.ui.geometry.Offset(x.toFloat(), 0f),
                    end = androidx.compose.ui.geometry.Offset((x - canvasHeight), canvasHeight),
                    color = color.copy(alpha = 0.3f),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }
    }
}
