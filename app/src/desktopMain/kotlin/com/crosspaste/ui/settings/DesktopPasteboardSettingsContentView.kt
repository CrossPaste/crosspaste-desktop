package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Block
import com.crosspaste.platform.Platform
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.NavigationManager
import com.crosspaste.ui.SourceControl
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import org.koin.compose.koinInject

@Composable
fun DesktopPasteboardSettingsContentView(platform: Platform) {
    val isWindows = remember { platform.isWindows() }
    PasteboardSettingsContentView(
        recordingExtContent = {
            ClipboardSourcesRow()
        },
        pastingExtContent = {
            if (isWindows) {
                WindowsPasteboardSettingsContentView()
            }
        },
    )
}

/**
 * Per-app exclusion is the fine-grained counterpart of the monitoring switch,
 * so it lives in the Recording card rather than under Extension.
 */
@Composable
private fun ClipboardSourcesRow() {
    val navigationManager = koinInject<NavigationManager>()
    val themeExt = LocalThemeExtState.current

    HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
    SettingListItem(
        title = "source_control_settings",
        subtitle = "source_control_settings_desc",
        icon = IconData(MaterialSymbols.Rounded.Block, themeExt.redIconColor),
    ) {
        navigationManager.navigate(SourceControl)
    }
}
