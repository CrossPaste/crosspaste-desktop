package com.crosspaste.ui.base

import androidx.compose.ui.graphics.Color

enum class BaseColor(val color: Color, val targetHue: Float) {
    Red(Color.Red, 0f),
    Blue(Color.Blue, 240f),
    Green(Color.Green, 120f),
    Yellow(Color.Yellow, 60f),
    Purple(Color(0xFF800080), 270f),
}
