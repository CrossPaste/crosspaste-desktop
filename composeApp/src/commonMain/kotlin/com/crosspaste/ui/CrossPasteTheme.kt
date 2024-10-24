package com.crosspaste.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.koin.compose.koinInject

object CrossPasteTheme {
    val LightColorScheme =
        lightColorScheme(
            primary = Color(0xFF167DFF),
            surface = Color(0xFFF0F0F0),
        )

    val DarkColorScheme =
        darkColorScheme(
            primary = Color(0xFFBB86FC),
            background = Color(0xFF23272A),
            surface = Color(0xFF323232),
        )

    @Composable
    fun Theme(content: @Composable () -> Unit) {
        val themeDetector = koinInject<ThemeDetector>()

        val colorScheme = themeDetector.getCurrentColorScheme()

        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }

    fun Color.darken(amount: Float): Color {
        return copy(
            red = (red * (1 - amount)).coerceAtLeast(0f),
            green = (green * (1 - amount)).coerceAtLeast(0f),
            blue = (blue * (1 - amount)).coerceAtLeast(0f),
        )
    }

    fun favoriteColor(): Color {
        return Color(0xFFFFCE34)
    }

    @Composable
    fun ColorScheme.selectColor(): Color {
        return if (isLight()) {
            Color(0xFFEBF6FF)
        } else {
            Color(0xFF2F446F)
        }
    }

    @Composable
    fun ColorScheme.isLight(): Boolean {
        return surface.luminance() > 0.5
    }

    fun connectedColor(): Color {
        return Color(0xFF95EC69)
    }

    fun connectingColor(): Color {
        return Color(0xFFE6C44D)
    }

    fun disconnectedColor(): Color {
        return Color(0xFFFF6969)
    }

    fun unmatchedColor(): Color {
        return Color(0xFF9A69EC)
    }

    fun unverifiedColor(): Color {
        return Color(0xFF69A9EC)
    }

    fun grantPermissionColor(): Color {
        return Color(0xFF95EC69)
    }
}
