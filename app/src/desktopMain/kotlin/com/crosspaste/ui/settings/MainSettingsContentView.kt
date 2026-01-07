package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.crosspaste.app.AppInfo
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.ui.About
import com.crosspaste.ui.NavigationManager
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import org.koin.compose.koinInject

@Composable
fun MainSettingsContentView() {
    val appInfo = koinInject<AppInfo>()
    val configManager = koinInject<DesktopConfigManager>()
    val navigationManager = koinInject<NavigationManager>()

    val config by configManager.config.collectAsState()

    SettingSectionCard {
        LanguageSettingItemView()
        HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
        FontSettingItemView()
        HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
        ThemeSettingItem()
        HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
        SettingListSwitchItem(
            title = "launch_at_startup",
            icon = Icons.Default.RocketLaunch,
            checked = config.enablePasteboardListening,
        ) {
            configManager.updateConfig("enableAutoStartUp", it)
        }
        HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
        SettingListItem(
            title = "about",
            subtitleContent = {
                Text("v${appInfo.displayVersion()}")
            },
            icon = Icons.Default.Info,
        ) {
            navigationManager.navigate(About)
        }
    }
}
