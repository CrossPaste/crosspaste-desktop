package com.crosspaste.ui

import androidx.compose.material3.ColorScheme
import com.crosspaste.ui.CrossPasteTheme.DarkColorScheme
import com.crosspaste.ui.CrossPasteTheme.LightColorScheme

interface ThemeDetector {
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

    fun getCurrentColorScheme(): ColorScheme {
        return if (isFollowSystem()) {
            if (isSystemInDark()) {
                DarkColorScheme
            } else {
                LightColorScheme
            }
        } else {
            if (isUserInDark()) {
                DarkColorScheme
            } else {
                LightColorScheme
            }
        }
    }
}
