package com.crosspaste.ui.settings

import androidx.compose.runtime.Composable
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Palette
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.LocalThemeState
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.theme.ThemeDetector
import org.koin.compose.koinInject

@Composable
fun ThemeSettingItem() {
    val copywriter = koinInject<GlobalCopywriter>()
    val themeDetector = koinInject<ThemeDetector>()
    val themeState = LocalThemeState.current
    val themeExt = LocalThemeExtState.current

    val themeOptions = listOf("light", "system", "dark")

    val selectedIndex =
        when {
            themeState.isFollowSystem -> 1
            themeState.isCurrentThemeDark -> 2
            else -> 0
        }

    SegmentedControlSettingsRow(
        title = copywriter.getText("theme"),
        icon = IconData(MaterialSymbols.Rounded.Palette, themeExt.purpleIconColor),
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
