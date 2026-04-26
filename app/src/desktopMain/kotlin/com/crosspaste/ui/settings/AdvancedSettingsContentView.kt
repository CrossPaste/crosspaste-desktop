package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Content_paste
import com.composables.icons.materialsymbols.rounded.Storage
import com.composables.icons.materialsymbols.rounded.Wifi
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.NavigationManager
import com.crosspaste.ui.NetworkSettings
import com.crosspaste.ui.PasteboardSettings
import com.crosspaste.ui.StorageSettings
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import org.koin.compose.koinInject

@Composable
fun AdvancedSettingsContentView() {
    val navigationManager = koinInject<NavigationManager>()
    val themeExt = LocalThemeExtState.current
    SettingSectionCard {
        SettingListItem(
            title = "pasteboard_settings",
            subtitle = "pasteboard_settings_desc",
            icon = IconData(MaterialSymbols.Rounded.Content_paste, themeExt.indigoIconColor),
        ) {
            navigationManager.navigate(PasteboardSettings)
        }
        HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
        SettingListItem(
            title = "network_settings",
            subtitle = "network_settings_desc",
            icon = IconData(MaterialSymbols.Rounded.Wifi, themeExt.greenIconColor),
        ) {
            navigationManager.navigate(NetworkSettings)
        }
        HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
        SettingListItem(
            title = "storage_settings",
            subtitle = "storage_settings_desc",
            icon = IconData(MaterialSymbols.Rounded.Storage, themeExt.amberIconColor),
        ) {
            navigationManager.navigate(StorageSettings)
        }
    }
}
