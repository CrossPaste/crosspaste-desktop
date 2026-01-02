package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crosspaste.app.AppInfo
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.About
import com.crosspaste.ui.NavigationManager
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import org.koin.compose.koinInject

@Composable
fun MainSettingsContentView() {
    val appInfo = koinInject<AppInfo>()
    val configManager = koinInject<DesktopConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val navigationManager = koinInject<NavigationManager>()

    val config by configManager.config.collectAsState()

    SettingSectionCard {
        LanguageSettingItemView()
        HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
        FontSettingItemView()
        HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(huge)
                    .padding(horizontal = medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Default.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(medium))
                Text(
                    copywriter.getText("theme"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            ThemeSegmentedPicker(modifier = Modifier.widthIn(max = 280.dp).height(xxxxLarge))
        }
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
