package com.crosspaste.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.Composable
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.LocalThemeState
import com.crosspaste.ui.theme.ThemeDetector
import org.koin.compose.koinInject

@Composable
fun ThemeSettingItem() {
    val copywriter = koinInject<GlobalCopywriter>()
    val themeDetector = koinInject<ThemeDetector>()
    val themeState = LocalThemeState.current

    val themeOptions = listOf("light", "system", "dark")

    val selectedIndex =
        when {
            themeState.isFollowSystem -> 1
            themeState.isCurrentThemeDark -> 2
            else -> 0
        }

    SegmentedControlSettingsRow(
        title = copywriter.getText("theme"),
        icon = Icons.Default.Palette,
        options = themeOptions,
        selectedOptionIndex = selectedIndex,
        optionLabel = { labelKey -> copywriter.getText(labelKey) },
        onOptionSelected = { index, _ ->
            when (index) {
                0 -> themeDetector.setThemeConfig(isFollowSystem = false, isUserInDark = false)
                1 -> themeDetector.setThemeConfig(isFollowSystem = true)
                2 -> themeDetector.setThemeConfig(isFollowSystem = false, isUserInDark = true)
            }
        },
    )
}
