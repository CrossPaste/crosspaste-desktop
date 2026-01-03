package com.crosspaste.ui.theme

import androidx.compose.material3.ColorScheme
import kotlinx.coroutines.flow.StateFlow

interface ThemeDetector {

    val themeState: StateFlow<ThemeState>

    fun setSystemInDark(isSystemInDark: Boolean)

    fun setThemeConfig(
        isFollowSystem: Boolean,
        isUserInDark: Boolean = false,
    )

    fun setThemeColor(themeColor: ThemeColor)
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
    }

    val isCurrentThemeDark: Boolean
        get() = if (isFollowSystem) isSystemInDark else isUserInDark
}
