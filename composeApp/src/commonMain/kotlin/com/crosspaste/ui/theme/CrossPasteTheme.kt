package com.crosspaste.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.koin.compose.koinInject

object CrossPasteTheme {

    @Composable
    fun Theme(content: @Composable () -> Unit) {
        val themeDetector = koinInject<ThemeDetector>()

        val colorScheme by themeDetector.getCurrentColorScheme().collectAsState()

        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }

    fun getThemeColor(name: String): ThemeColor {
        return when (name) {
            CoralColor.name -> CoralColor
            GrassColor.name -> GrassColor
            HoneyColor.name -> HoneyColor
            SeaColor.name -> SeaColor
            else -> GrassColor
        }
    }

    @Composable
    fun ColorScheme.favoriteColor(): Color {
        return if (isLight()) {
            Color(0xFFFFAA00)
        } else {
            Color(0xFFFFCE34)
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
