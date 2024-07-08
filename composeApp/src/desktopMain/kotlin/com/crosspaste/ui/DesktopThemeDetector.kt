package com.crosspaste.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crosspaste.config.ConfigManager
import com.jthemedetecor.OsThemeDetector

class DesktopThemeDetector(private val configManager: ConfigManager) : ThemeDetector {

    private val detector = OsThemeDetector.getDetector()

    private var _isFollowSystem: Boolean by mutableStateOf(configManager.config.isFollowSystemTheme)

    private var _isSystemInDark: Boolean by mutableStateOf(detector.isDark)

    private var _isUserInDark: Boolean by mutableStateOf(configManager.config.isDarkTheme)

    private val listeners: MutableList<(Boolean) -> Unit> = mutableListOf()

    init {
        detector.registerListener { isDark: Boolean ->
            _isSystemInDark = isDark

            for (listener in listeners) {
                listener(isCurrentThemeDark())
            }
        }
    }

    override fun isSystemInDark(): Boolean {
        return _isSystemInDark
    }

    override fun isFollowSystem(): Boolean {
        return _isFollowSystem
    }

    override fun isUserInDark(): Boolean {
        return _isUserInDark
    }

    override fun setThemeConfig(
        isFollowSystem: Boolean,
        isUserInDark: Boolean,
    ) {
        _isFollowSystem = isFollowSystem
        _isUserInDark = isUserInDark
        configManager.updateConfig(listOf("isFollowSystemTheme", "isDarkTheme"), listOf(isFollowSystem, isUserInDark))
        for (listener in listeners) {
            listener(isCurrentThemeDark())
        }
    }

    override fun addListener(listener: (Boolean) -> Unit) {
        listeners.add(listener)
    }
}
