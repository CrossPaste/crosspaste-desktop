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

    companion object {
        fun createThemeState(
            themeColor: ThemeColor,
            colorContrast: ColorContrast,
            isFollowSystem: Boolean,
            isUserInDark: Boolean,
            isSystemInDark: Boolean,
        ): ThemeState {
            val isCurrentThemeDark = if (isFollowSystem) isSystemInDark else isUserInDark
            val currentColorScheme =
                if (isCurrentThemeDark) {
                    getDarkColorSchema(themeColor, colorContrast)
                } else {
                    getLightColorSchema(themeColor, colorContrast)
                }
            return ThemeState(
                themeColor = themeColor,
                colorContrast = colorContrast,
                isFollowSystem = isFollowSystem,
                isUserInDark = isUserInDark,
                isSystemInDark = isSystemInDark,
                colorScheme = currentColorScheme,
            )
        }

        private fun getLightColorSchema(
            themeColor: ThemeColor,
            contrast: ColorContrast,
        ): ColorScheme =
            when (contrast) {
                ColorContrast.Standard -> themeColor.lightColorScheme
                ColorContrast.Medium -> themeColor.lightMediumContrastColorScheme
                ColorContrast.High -> themeColor.lightHighContrastColorScheme
            }

        private fun getDarkColorSchema(
            themeColor: ThemeColor,
            contrast: ColorContrast,
        ): ColorScheme =
            when (contrast) {
                ColorContrast.Standard -> themeColor.darkColorScheme
                ColorContrast.Medium -> themeColor.darkMediumContrastColorScheme
                ColorContrast.High -> themeColor.darkHighContrastColorScheme
            }
    }

    val isCurrentThemeDark: Boolean
        get() = if (isFollowSystem) isSystemInDark else isUserInDark
}
