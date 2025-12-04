package com.crosspaste.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.crosspaste.ui.LocalThemeState
import com.crosspaste.ui.base.BaseColor
import com.crosspaste.ui.base.rememberUserSelectedFont
import com.crosspaste.ui.base.withCustomFonts
import com.crosspaste.utils.ColorUtils.getAdaptiveColor
import org.koin.compose.koinInject

object CrossPasteTheme {

    @Composable
    fun Theme(content: @Composable () -> Unit) {
        val themeDetector = koinInject<ThemeDetector>()
        val themeState by themeDetector.themeState.collectAsState()

        val userSelectedFont by rememberUserSelectedFont()
        MaterialTheme(
            colorScheme = themeState.colorScheme,
            typography = MaterialTheme.typography.withCustomFonts(userSelectedFont),
        ) {
            CompositionLocalProvider(
                LocalThemeState provides themeState,
            ) {
                content()
            }
        }
    }

    fun getThemeColor(name: String): ThemeColor =
        when (name) {
            CoralColor.name -> CoralColor
            GrassColor.name -> GrassColor
            HoneyColor.name -> HoneyColor
            SeaColor.name -> SeaColor
            else -> GrassColor
        }

    fun connectedColor(backgroundColor: Color): Color = getAdaptiveColor(backgroundColor, BaseColor.Green.targetHue)

    fun connectingColor(backgroundColor: Color): Color = getAdaptiveColor(backgroundColor, BaseColor.Yellow.targetHue)

    fun disconnectedColor(backgroundColor: Color): Color = getAdaptiveColor(backgroundColor, BaseColor.Red.targetHue)

    fun unmatchedColor(backgroundColor: Color): Color = getAdaptiveColor(backgroundColor, BaseColor.Purple.targetHue)

    fun unverifiedColor(backgroundColor: Color): Color = getAdaptiveColor(backgroundColor, BaseColor.Blue.targetHue)
}
