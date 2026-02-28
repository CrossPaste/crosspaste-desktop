package com.crosspaste.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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

        val isSystemInDark = isSystemInDarkTheme()

        val themeState by themeDetector.themeState.collectAsState()

        val userSelectedFont by rememberUserSelectedFont()

        LaunchedEffect(isSystemInDark) {
            themeDetector.setSystemInDark(isSystemInDark)
        }

        // Use Compose-detected isSystemInDark directly to avoid theme flash on startup.
        // DesktopThemeDetector initializes isSystemInDark to false, and the LaunchedEffect
        // above corrects it asynchronously — but that causes one frame of wrong theme.
        val effectiveThemeState =
            remember(themeState, isSystemInDark) {
                if (themeState.isSystemInDark != isSystemInDark) {
                    createThemeState(
                        themeColor = themeState.themeColor,
                        isFollowSystem = themeState.isFollowSystem,
                        isUserInDark = themeState.isUserInDark,
                        isSystemInDark = isSystemInDark,
                    )
                } else {
                    themeState
                }
            }

        val themeExt = ThemeExt.buildThemeExt(effectiveThemeState.isCurrentThemeDark)

        CompositionLocalProvider(LocalThemeExtState provides themeExt) {
            MaterialTheme(
                colorScheme = effectiveThemeState.colorScheme,
                typography = MaterialTheme.typography.withCustomFonts(userSelectedFont),
            ) {
                CompositionLocalProvider(
                    LocalThemeState provides effectiveThemeState,
                ) {
                    content()
                }
            }
        }
    }
}
