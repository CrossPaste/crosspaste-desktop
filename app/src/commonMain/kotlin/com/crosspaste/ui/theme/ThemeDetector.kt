package com.crosspaste.ui.theme

import androidx.compose.material3.ColorScheme
import kotlinx.coroutines.flow.StateFlow

interface ThemeDetector {

    val themeState: StateFlow<ThemeState>

    fun setThemeConfig(
        isFollowSystem: Boolean,
        isUserInDark: Boolean = false,
    )

    fun setThemeColor(themeColor: ThemeColor)

    fun setColorContrast(colorContrast: ColorContrast)
}

data class ThemeState(
    val themeColor: ThemeColor,
    val colorContrast: ColorContrast,
    val isFollowSystem: Boolean,
    val isUserInDark: Boolean,
    val isSystemInDark: Boolean,
    val colorScheme: ColorScheme,
) {

    val isCurrentThemeDark: Boolean
        get() = if (isFollowSystem) isSystemInDark else isUserInDark
}
