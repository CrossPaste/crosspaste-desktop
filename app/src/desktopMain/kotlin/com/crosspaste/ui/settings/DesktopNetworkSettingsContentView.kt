package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Share
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import org.koin.compose.koinInject

@Composable
fun DesktopNetworkSettingsContentView() {
    NetworkSettingsContentView(
        syncExtContent = {
            DesktopSyncExtContent()
        },
    )
}

@Composable
private fun DesktopSyncExtContent() {
    val configManager = koinInject<DesktopConfigManager>()
    val themeExt = LocalThemeExtState.current

    val config by configManager.config.collectAsState()

    HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
    SettingListSwitchItem(
        title = "enable_clipboard_relay",
        icon = IconData(MaterialSymbols.Rounded.Share, themeExt.yellowIconColor),
        checked = config.enableClipboardRelay,
    ) { enableClipboardRelay ->
        configManager.updateConfig("enableClipboardRelay", enableClipboardRelay)
    }
}
