package com.crosspaste.ui

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
}
