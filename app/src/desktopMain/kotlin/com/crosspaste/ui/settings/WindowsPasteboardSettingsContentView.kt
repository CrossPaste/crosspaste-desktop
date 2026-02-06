package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Desktop_windows
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import org.koin.compose.koinInject

@Composable
fun WindowsPasteboardSettingsContentView() {
    val configManager = koinInject<DesktopConfigManager>()
    val themeExt = LocalThemeExtState.current

    val config by configManager.config.collectAsState()

    HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))

    SettingListSwitchItem(
        title = "legacy_software_compatibility",
        icon = IconData(MaterialSymbols.Rounded.Desktop_windows, themeExt.indigoIconColor),
        checked = config.legacySoftwareCompatibility,
    ) { newLegacySoftwareCompatibility ->
        configManager.updateConfig(
            "legacySoftwareCompatibility",
            newLegacySoftwareCompatibility,
        )
    }
}
