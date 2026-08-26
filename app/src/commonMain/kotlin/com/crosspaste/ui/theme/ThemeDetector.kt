package com.crosspaste.ui.theme

import androidx.compose.material3.ColorScheme
import kotlinx.coroutines.flow.StateFlow

interface ThemeDetector {

    val themeConfig: StateFlow<ThemeConfig>

    fun setThemeConfig(
        isFollowSystem: Boolean,
        isUserInDark: Boolean = false,
    )
}

/**
 * User-controlled theme preferences. The system dark-mode value is intentionally
 * not part of this state: it is read in composition via `isSystemInDarkTheme()`
 * (dynamic since Compose Multiplatform 1.12) and resolved against this config.
 */
data class ThemeConfig(
    val themeColor: ThemeColor,
    val isFollowSystem: Boolean,
    val isUserInDark: Boolean,
) {

    fun resolveIsDark(isSystemInDark: Boolean): Boolean = if (isFollowSystem) isSystemInDark else isUserInDark
}

data class ThemeState(
    val themeColor: ThemeColor,
    val isFollowSystem: Boolean,
    val isUserInDark: Boolean,
    val isSystemInDark: Boolean,
    val colorScheme: ColorScheme,
) {

    companion object {
        fun createThemeState(
            themeColor: ThemeColor,
            isFollowSystem: Boolean,
            isUserInDark: Boolean,
            isSystemInDark: Boolean,
        ): ThemeState {
            val isCurrentThemeDark = if (isFollowSystem) isSystemInDark else isUserInDark
            val currentColorScheme =
                if (isCurrentThemeDark) {
                    themeColor.darkColorScheme
                } else {
                    themeColor.lightColorScheme
                }
            return ThemeState(
                themeColor = themeColor,
                isFollowSystem = isFollowSystem,
                isUserInDark = isUserInDark,
                isSystemInDark = isSystemInDark,
                colorScheme = currentColorScheme,
            )
        }

        fun createThemeState(
            themeConfig: ThemeConfig,
            isSystemInDark: Boolean,
        ): ThemeState =
            createThemeState(
                themeColor = themeConfig.themeColor,
                isFollowSystem = themeConfig.isFollowSystem,
                isUserInDark = themeConfig.isUserInDark,
                isSystemInDark = isSystemInDark,
            )
    }

    val isCurrentThemeDark: Boolean
        get() = if (isFollowSystem) isSystemInDark else isUserInDark
}
