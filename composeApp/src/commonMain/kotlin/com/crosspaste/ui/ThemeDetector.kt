package com.crosspaste.ui

import androidx.compose.material3.ColorScheme
import com.crosspaste.ui.theme.ThemeColor
import kotlinx.coroutines.flow.StateFlow

interface ThemeDetector {

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

    fun isThemeColor(themeColor: ThemeColor): Boolean

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
