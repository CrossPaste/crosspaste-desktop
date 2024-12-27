package com.crosspaste.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.crosspaste.utils.ColorUtils.getAdaptiveColor
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

    fun connectedColor(backgroundColor: Color): Color {
        return getAdaptiveColor(backgroundColor, 120f)
    }

    fun connectingColor(backgroundColor: Color): Color {
        return getAdaptiveColor(backgroundColor, 60f)
    }

    fun disconnectedColor(backgroundColor: Color): Color {
        return getAdaptiveColor(backgroundColor, 0f)
    }

    fun unmatchedColor(backgroundColor: Color): Color {
        return getAdaptiveColor(backgroundColor, 270f)
    }

    fun unverifiedColor(backgroundColor: Color): Color {
        return getAdaptiveColor(backgroundColor, 240f)
    }
}
