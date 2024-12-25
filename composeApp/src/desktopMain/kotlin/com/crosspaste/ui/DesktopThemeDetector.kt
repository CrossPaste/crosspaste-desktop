package com.crosspaste.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crosspaste.config.ConfigManager
import com.crosspaste.ui.theme.CrossPasteTheme
import com.crosspaste.ui.theme.ThemeColor
import com.jthemedetecor.OsThemeDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DesktopThemeDetector(private val configManager: ConfigManager) : ThemeDetector {

    private val detector = OsThemeDetector.getDetector()

    private val _currentThemeColor =
        MutableStateFlow(
            CrossPasteTheme.getThemeColor(configManager.config.themeColor),
        )
    private val currentThemeColor: StateFlow<ThemeColor> = _currentThemeColor

    private val _lightColorScheme =
        MutableStateFlow(
            currentThemeColor.value.lightColorScheme,
        )
    override val lightColorScheme: StateFlow<ColorScheme> = _lightColorScheme

    private val _darkColorScheme =
        MutableStateFlow(
            currentThemeColor.value.darkColorScheme,
        )
    override val darkColorScheme: StateFlow<ColorScheme> = _darkColorScheme

    private var _isFollowSystem: Boolean by mutableStateOf(configManager.config.isFollowSystemTheme)

    private var _isSystemInDark: Boolean by mutableStateOf(detector.isDark)

    private var _isUserInDark: Boolean by mutableStateOf(configManager.config.isDarkTheme)

    init {
        detector.registerListener { isDark: Boolean ->
            _isSystemInDark = isDark
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
    }

    override fun setThemeColor(themeColor: ThemeColor) {
        _currentThemeColor.value = themeColor
        _lightColorScheme.value = themeColor.lightColorScheme
        _darkColorScheme.value = themeColor.darkColorScheme
        configManager.updateConfig("themeColor", themeColor.name)
    }

    override fun isThemeColor(themeColor: ThemeColor): Boolean {
        return currentThemeColor.value == themeColor
    }
}
