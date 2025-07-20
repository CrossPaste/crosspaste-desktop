package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.ui.base.CustomSwitch
import com.crosspaste.ui.base.file
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.xxxLarge
import org.koin.compose.koinInject

@Composable
fun WindowsPasteboardSettingsContentView() {
    val configManager = koinInject<DesktopConfigManager>()

    val config by configManager.config.collectAsState()

    HorizontalDivider(modifier = Modifier.padding(start = xxxLarge))

    SettingItemView(
        painter = file(),
        text = "legacy_software_compatibility",
    ) {
        CustomSwitch(
            modifier =
                Modifier
                    .width(medium * 2)
                    .height(large2X),
            checked = config.legacySoftwareCompatibility,
            onCheckedChange = { newLegacySoftwareCompatibility ->
                configManager.updateConfig(
                    "legacySoftwareCompatibility",
                    newLegacySoftwareCompatibility,
                )
            },
        )
    }
}
