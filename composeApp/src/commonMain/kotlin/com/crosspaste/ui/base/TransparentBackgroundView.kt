package com.crosspaste.ui.base

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.min

@Composable
fun TransparentBackground(modifier: Modifier = Modifier) {
    val background = Color.White
    val gray = Color(0xFFE2E2E2)
    Canvas(modifier = modifier) {
        val stepSize = 5.dp.toPx()
        val columns = (size.width / stepSize).toInt()
        val rows = (size.height / stepSize).toInt()

        for (i in 0..columns) {
            for (j in 0..rows) {
                val color = if ((i + j) % 2 == 0) gray else background
                val offsetWidth = i * stepSize
                val offsetHeight = j * stepSize
                val widthSize = min(size.width - offsetWidth, stepSize)
                val heightSize = min(size.height - offsetHeight, stepSize)
                drawRect(
                    color = color,
                    topLeft = Offset(i * stepSize, j * stepSize),
                    size = Size(widthSize, heightSize),
                )
            }
        }
    }
}
