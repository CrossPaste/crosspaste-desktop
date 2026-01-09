package com.crosspaste.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.LocalThemeState
import com.crosspaste.ui.base.rememberUserSelectedFont
import com.crosspaste.ui.base.withCustomFonts
import org.koin.compose.koinInject

object CrossPasteTheme {

    @Composable
    fun Theme(content: @Composable () -> Unit) {
        val themeDetector = koinInject<ThemeDetector>()

        val isSystemInDark = isSystemInDarkTheme()

        val themeState by themeDetector.themeState.collectAsState()

        val userSelectedFont by rememberUserSelectedFont()

        LaunchedEffect(isSystemInDark) {
            themeDetector.setSystemInDark(isSystemInDark)
        }

        val primary = themeState.colorScheme.primary

        val themeExt = ThemeExt.buildThemeExt(primary, themeState.isCurrentThemeDark)

        CompositionLocalProvider(LocalThemeExtState provides themeExt) {
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
    }

    fun getThemeColor(name: String): ThemeColor =
        when (name) {
            CoralColor.name -> CoralColor
            GrassColor.name -> GrassColor
            HoneyColor.name -> HoneyColor
            SeaColor.name -> SeaColor
            else -> GrassColor
        }
}
