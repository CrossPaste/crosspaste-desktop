package com.crosspaste.ui.theme

import androidx.compose.material3.ColorScheme
import kotlinx.coroutines.flow.StateFlow

interface ThemeDetector {

    val currentThemeColor: StateFlow<ThemeColor>

    val colorContrast: StateFlow<ColorContrast>

    val lightColorScheme: StateFlow<ColorScheme>

    val darkColorScheme: StateFlow<ColorScheme>

    fun isSystemInDark(): Boolean

    fun isFollowSystem(): Boolean

    fun isUserInDark(): Boolean

    fun isCurrentThemeDark(): Boolean {
        return isFollowSystem() && isSystemInDark() || !isFollowSystem() && isUserInDark()
    }

    fun setThemeConfig(
        isFollowSystem: Boolean,
        isUserInDark: Boolean = false,
    )

    fun setThemeColor(themeColor: ThemeColor)

    fun setColorContrast(colorContrast: ColorContrast)

    fun getCurrentColorScheme(): StateFlow<ColorScheme> {
        return if (isFollowSystem()) {
            if (isSystemInDark()) {
                darkColorScheme
            } else {
                lightColorScheme
            }
        } else {
            if (isUserInDark()) {
                darkColorScheme
            } else {
                lightColorScheme
            }
        }
    }
}
