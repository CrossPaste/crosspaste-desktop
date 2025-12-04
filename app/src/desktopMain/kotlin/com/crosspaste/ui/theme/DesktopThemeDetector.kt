package com.crosspaste.ui.theme

import androidx.compose.material3.ColorScheme
import com.crosspaste.config.CommonConfigManager
import com.jthemedetecor.OsThemeDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class DesktopThemeDetector(
    private val configManager: CommonConfigManager,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) : ThemeDetector {

    private val detector = OsThemeDetector.getDetector()

    private val initialThemeColor = CrossPasteTheme.getThemeColor(configManager.getCurrentConfig().themeColor)
    private val initialColorContrast = ColorContrast.valueOf(configManager.getCurrentConfig().colorContrast)
    private val initialFollowSystem = configManager.getCurrentConfig().isFollowSystemTheme
    private val initialIsDarkTheme = configManager.getCurrentConfig().isDarkTheme

    private val _themeColor = MutableStateFlow(initialThemeColor)
    private val _colorContrast = MutableStateFlow(initialColorContrast)
    private val _isFollowSystem = MutableStateFlow(initialFollowSystem)
    private val _isUserInDark = MutableStateFlow(initialIsDarkTheme)
    private val _isSystemInDark = MutableStateFlow(detector.isDark)

    override val themeState: StateFlow<ThemeState> =
        combine(
            _themeColor,
            _colorContrast,
            _isFollowSystem,
            _isUserInDark,
            _isSystemInDark,
        ) { color, contrast, follow, user, system ->

            createThemeState(
                themeColor = color,
                colorContrast = contrast,
                isFollowSystem = follow,
                isUserInDark = user,
                isSystemInDark = system,
            )
        }.stateIn(
            scope,
            SharingStarted.Eagerly,
            initialValue =
                createThemeState(
                    themeColor = initialThemeColor,
                    colorContrast = initialColorContrast,
                    isFollowSystem = initialFollowSystem,
                    isUserInDark = initialIsDarkTheme,
                    isSystemInDark = detector.isDark,
                ),
        )

    init {
        detector.registerListener { isDark: Boolean ->
            _isSystemInDark.value = isDark
        }
    }

    private fun createThemeState(
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

    override fun setThemeConfig(
        isFollowSystem: Boolean,
        isUserInDark: Boolean,
    ) {
        _isFollowSystem.value = isFollowSystem
        _isUserInDark.value = isUserInDark

        configManager.updateConfig(
            listOf("isFollowSystemTheme", "isDarkTheme"),
            listOf(isFollowSystem, isUserInDark),
        )
    }

    override fun setThemeColor(themeColor: ThemeColor) {
        _themeColor.value = themeColor
        configManager.updateConfig("themeColor", themeColor.name)
    }

    override fun setColorContrast(colorContrast: ColorContrast) {
        _colorContrast.value = colorContrast
        configManager.updateConfig("colorContrast", colorContrast.name)
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
