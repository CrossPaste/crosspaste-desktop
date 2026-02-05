package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
            icon = IconData(Icons.Default.ContentPaste, themeExt.indigoIconColor),
        ) {
            navigationManager.navigate(PasteboardSettings)
        }
        HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
        SettingListItem(
            title = "network_settings",
            subtitle = "network_settings_desc",
            icon = IconData(Icons.Default.Wifi, themeExt.greenIconColor),
        ) {
            navigationManager.navigate(NetworkSettings)
        }
        HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
        SettingListItem(
            title = "storage_settings",
            subtitle = "storage_settings_desc",
            icon = IconData(Icons.Default.Storage, themeExt.amberIconColor),
        ) {
            navigationManager.navigate(StorageSettings)
        }
    }
}
