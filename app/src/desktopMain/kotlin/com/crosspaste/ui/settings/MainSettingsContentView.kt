package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Info
import com.composables.icons.materialsymbols.rounded.Palette
import com.composables.icons.materialsymbols.rounded.Rocket_launch
import com.composables.icons.materialsymbols.rounded.System_update
import com.crosspaste.app.AppInfo
import com.crosspaste.app.WindowsUpdateChannel
import com.crosspaste.app.WindowsZipUpdater
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.ui.About
import com.crosspaste.ui.AppearanceSettings
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.NavigationManager
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import org.koin.compose.koinInject

@Composable
fun MainSettingsContentView() {
    val appInfo = koinInject<AppInfo>()
    val configManager = koinInject<DesktopConfigManager>()
    val navigationManager = koinInject<NavigationManager>()
    val windowsZipUpdater = koinInject<WindowsZipUpdater>()
    val themeExt = LocalThemeExtState.current

    // Only the portable zip updates itself in-app; Store / Conveyor installs are
    // updated by the OS, so the switch would do nothing there.
    val inAppUpdates = remember { windowsZipUpdater.channel == WindowsUpdateChannel.PORTABLE_ZIP }

    val config by configManager.config.collectAsState()

    SettingSectionCard {
        LanguageSettingItemView()
        HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
        SettingListItem(
            title = "appearance_settings",
            subtitle = "appearance_settings_desc",
            icon = IconData(MaterialSymbols.Rounded.Palette, themeExt.purpleIconColor),
        ) {
            navigationManager.navigate(AppearanceSettings)
        }
        HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
        SettingListSwitchItem(
            title = "launch_at_startup",
            icon = IconData(MaterialSymbols.Rounded.Rocket_launch, themeExt.roseIconColor),
            checked = config.enableAutoStartUp,
        ) {
            configManager.updateConfig("enableAutoStartUp", it)
        }
        if (inAppUpdates) {
            HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
            SettingListSwitchItem(
                title = "auto_download_update",
                icon = IconData(MaterialSymbols.Rounded.System_update, themeExt.greenIconColor),
                checked = config.autoDownloadUpdate,
            ) {
                configManager.updateConfig("autoDownloadUpdate", it)
            }
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
