package com.crosspaste.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.LocalThemeState
import com.crosspaste.ui.base.rememberUserSelectedFont
import com.crosspaste.ui.base.withCustomFonts
import com.crosspaste.ui.theme.ThemeState.Companion.createThemeState
import org.koin.compose.koinInject

object CrossPasteTheme {

    @Composable
    fun Theme(content: @Composable () -> Unit) {
        val themeDetector = koinInject<ThemeDetector>()

        val themeConfig by themeDetector.themeConfig.collectAsState()

        // Dynamic since Compose Multiplatform 1.12: recomposes when the OS theme
        // changes, and is already correct on the first frame — so the resolved
        // theme never needs an async correction step.
        val isSystemInDark = isSystemInDarkTheme()

        val userSelectedFont by rememberUserSelectedFont()

        val themeState =
            remember(themeConfig, isSystemInDark) {
                createThemeState(themeConfig, isSystemInDark)
            }

        val themeExt = ThemeExt.buildThemeExt(themeState.isCurrentThemeDark)

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
}
