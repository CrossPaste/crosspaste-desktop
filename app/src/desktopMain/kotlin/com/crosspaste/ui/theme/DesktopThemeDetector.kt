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
import kotlinx.coroutines.flow.stateIn

class DesktopThemeDetector(
    private val configManager: CommonConfigManager,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + mainDispatcher),
) : ThemeDetector {

    private val initialThemeColor = CrossPasteColor
    private val initialFollowSystem = configManager.getCurrentConfig().isFollowSystemTheme
    private val initialIsDarkTheme = configManager.getCurrentConfig().isDarkTheme

    private val _themeColor = MutableStateFlow(initialThemeColor)
    private val _isFollowSystem = MutableStateFlow(initialFollowSystem)
    private val _isUserInDark = MutableStateFlow(initialIsDarkTheme)
    private val _isSystemInDark = MutableStateFlow(false)

    override val themeState: StateFlow<ThemeState> =
        combine(
            _themeColor,
            _isFollowSystem,
            _isUserInDark,
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
        _isFollowSystem.value = isFollowSystem
        _isUserInDark.value = isUserInDark

        configManager.updateConfig(
            listOf("isFollowSystemTheme", "isDarkTheme"),
            listOf(isFollowSystem, isUserInDark),
        )
    }
}
