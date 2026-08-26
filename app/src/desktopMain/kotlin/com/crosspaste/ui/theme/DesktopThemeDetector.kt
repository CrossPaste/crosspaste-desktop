package com.crosspaste.ui.theme

import com.crosspaste.config.CommonConfigManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DesktopThemeDetector(
    private val configManager: CommonConfigManager,
) : ThemeDetector {

    private val _themeConfig =
        MutableStateFlow(
            ThemeConfig(
                themeColor = CrossPasteColor,
                isFollowSystem = configManager.getCurrentConfig().isFollowSystemTheme,
                isUserInDark = configManager.getCurrentConfig().isDarkTheme,
            ),
        )

    override val themeConfig: StateFlow<ThemeConfig> = _themeConfig.asStateFlow()

    override fun setThemeConfig(
        isFollowSystem: Boolean,
        isUserInDark: Boolean,
    ) {
        // Atomic update: both values change in a single emission to avoid
        // an intermediate theme state flash from updating them separately.
        _themeConfig.update {
            it.copy(
                isFollowSystem = isFollowSystem,
                isUserInDark = isUserInDark,
            )
        }

        configManager.updateConfig(
            listOf("isFollowSystemTheme", "isDarkTheme"),
            listOf(isFollowSystem, isUserInDark),
        )
    }
}
