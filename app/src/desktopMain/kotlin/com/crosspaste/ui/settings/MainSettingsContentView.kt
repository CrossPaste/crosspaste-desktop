package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Info
import com.composables.icons.materialsymbols.rounded.Keyboard
import com.composables.icons.materialsymbols.rounded.Tune
import com.crosspaste.app.AppInfo
import com.crosspaste.ui.About
import com.crosspaste.ui.AppearanceSettings
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.NavigationManager
import com.crosspaste.ui.ShortcutKeys
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import org.koin.compose.koinInject

@Composable
fun MainSettingsContentView() {
    val appInfo = koinInject<AppInfo>()
    val navigationManager = koinInject<NavigationManager>()
    val themeExt = LocalThemeExtState.current

    SettingSectionCard {
        SettingListItem(
            title = "appearance_settings",
            subtitle = "appearance_settings_desc",
            icon = IconData(MaterialSymbols.Rounded.Tune, themeExt.purpleIconColor),
        ) {
            navigationManager.navigate(AppearanceSettings)
        }
        HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
        SettingListItem(
            title = "shortcut_keys",
            icon = IconData(MaterialSymbols.Rounded.Keyboard, themeExt.cyanIconColor),
        ) {
            navigationManager.navigate(ShortcutKeys)
        }
        HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
        SettingListItem(
            title = "about",
            subtitleContent = {
                Text("v${appInfo.displayVersion()}")
            },
            icon = IconData(MaterialSymbols.Rounded.Info, themeExt.blueIconColor),
        ) {
            navigationManager.navigate(About)
        }
    }
}
