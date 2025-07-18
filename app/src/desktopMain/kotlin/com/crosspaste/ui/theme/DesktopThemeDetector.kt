package com.crosspaste.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crosspaste.config.CommonConfigManager
import com.jthemedetecor.OsThemeDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DesktopThemeDetector(
    private val configManager: CommonConfigManager,
) : ThemeDetector {

    private val detector = OsThemeDetector.getDetector()

    private val _currentThemeColor =
        MutableStateFlow(
            CrossPasteTheme.getThemeColor(configManager.getCurrentConfig().themeColor),
        )
    override val currentThemeColor: StateFlow<ThemeColor> = _currentThemeColor

    private val _colorContrast =
        MutableStateFlow(
            ColorContrast.valueOf(configManager.getCurrentConfig().colorContrast),
        )
    override val colorContrast: StateFlow<ColorContrast> = _colorContrast

    private val _lightColorScheme = MutableStateFlow(getLightColorSchema(currentThemeColor.value))
    override val lightColorScheme: StateFlow<ColorScheme> = _lightColorScheme

    private val _darkColorScheme = MutableStateFlow(getDarkColorSchema(currentThemeColor.value))
    override val darkColorScheme: StateFlow<ColorScheme> = _darkColorScheme

    private var _isFollowSystem: Boolean by mutableStateOf(
        configManager.getCurrentConfig().isFollowSystemTheme,
    )

    private var _isSystemInDark: Boolean by mutableStateOf(detector.isDark)

    private var _isUserInDark: Boolean by mutableStateOf(
        configManager.getCurrentConfig().isDarkTheme,
    )

    init {
        detector.registerListener { isDark: Boolean ->
            _isSystemInDark = isDark
        }
    }

    override fun isSystemInDark(): Boolean = _isSystemInDark

    override fun isFollowSystem(): Boolean = _isFollowSystem

    override fun isUserInDark(): Boolean = _isUserInDark

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
        _lightColorScheme.value = getLightColorSchema(themeColor)
        _darkColorScheme.value = getDarkColorSchema(themeColor)
        configManager.updateConfig("themeColor", themeColor.name)
    }

    override fun setColorContrast(colorContrast: ColorContrast) {
        _colorContrast.value = colorContrast
        _lightColorScheme.value = getLightColorSchema(currentThemeColor.value)
        _darkColorScheme.value = getDarkColorSchema(currentThemeColor.value)
        configManager.updateConfig("colorContrast", colorContrast.name)
    }

    private fun getLightColorSchema(themeColor: ThemeColor): ColorScheme =
        when (colorContrast.value) {
            ColorContrast.Standard -> themeColor.lightColorScheme
            ColorContrast.Medium -> themeColor.lightMediumContrastColorScheme
            ColorContrast.High -> themeColor.lightHighContrastColorScheme
        }

    private fun getDarkColorSchema(themeColor: ThemeColor): ColorScheme =
        when (colorContrast.value) {
            ColorContrast.Standard -> themeColor.darkColorScheme
            ColorContrast.Medium -> themeColor.darkMediumContrastColorScheme
            ColorContrast.High -> themeColor.darkHighContrastColorScheme
        }
}
