package com.crosspaste.ui.theme

import com.crosspaste.config.CommonConfigManager
import com.crosspaste.ui.theme.ThemeState.Companion.createThemeState
import com.crosspaste.utils.mainDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class DesktopThemeDetector(
    private val configManager: CommonConfigManager,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + mainDispatcher),
) : ThemeDetector {

    private data class ThemeConfig(
        val isFollowSystem: Boolean,
        val isUserInDark: Boolean,
    )

    private val initialThemeColor = CrossPasteColor
    private val initialFollowSystem = configManager.getCurrentConfig().isFollowSystemTheme
    private val initialIsDarkTheme = configManager.getCurrentConfig().isDarkTheme

    private val _themeColor = MutableStateFlow(initialThemeColor)
    private val _themeConfig = MutableStateFlow(ThemeConfig(initialFollowSystem, initialIsDarkTheme))

    // Initial system dark mode is false; the actual value is set shortly after
    // startup when CrossPasteTheme's LaunchedEffect calls setSystemInDark().
    private val _isSystemInDark = MutableStateFlow(false)

    override val themeState: StateFlow<ThemeState> =
        combine(
            _themeColor,
            _themeConfig.map { it.isFollowSystem },
            _themeConfig.map { it.isUserInDark },
            _isSystemInDark,
        ) { color, follow, user, system ->

            createThemeState(
                themeColor = color,
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
                    isFollowSystem = initialFollowSystem,
                    isUserInDark = initialIsDarkTheme,
                    isSystemInDark = false,
                ),
        )

    override fun setSystemInDark(isSystemInDark: Boolean) {
        _isSystemInDark.value = isSystemInDark
    }

    override fun setThemeConfig(
        isFollowSystem: Boolean,
        isUserInDark: Boolean,
    ) {
        // Atomic update: both values change in a single emission to avoid
        // an intermediate theme state flash from updating them separately.
        _themeConfig.value = ThemeConfig(isFollowSystem, isUserInDark)

        configManager.updateConfig(
            listOf("isFollowSystemTheme", "isDarkTheme"),
            listOf(isFollowSystem, isUserInDark),
        )
    }
}
