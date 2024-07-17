package com.crosspaste.ui.base

import androidx.compose.foundation.Canvas
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun TransparentBackground(modifier: Modifier = Modifier) {
    val background = MaterialTheme.colors.background
    Canvas(modifier = modifier) {
        val stepSize = 5.dp.toPx()
        val columns = (size.width / stepSize).toInt()
        val rows = (size.height / stepSize).toInt()

        for (i in 0 until columns) {
            for (j in 0 until rows) {
                val color = if ((i + j) % 2 == 0) Color.LightGray else background
                drawRect(
                    color = color,
                    topLeft = Offset(i * stepSize, j * stepSize),
                    size = Size(stepSize, stepSize),
                )
            }
        }
    }
}
