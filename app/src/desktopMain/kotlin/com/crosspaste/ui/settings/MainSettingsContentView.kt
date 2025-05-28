package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crosspaste.app.AppControl
import com.crosspaste.config.ConfigManager
import com.crosspaste.log.CrossPasteLogger
import com.crosspaste.paste.PasteboardService
import com.crosspaste.ui.base.HighlightedCard
import com.crosspaste.ui.base.bell
import com.crosspaste.ui.base.bolt
import com.crosspaste.ui.base.clipboard
import com.crosspaste.ui.base.debug
import com.crosspaste.ui.base.palette
import com.crosspaste.ui.base.shield
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import org.koin.compose.koinInject

@Composable
fun MainSettingsContentView() {
    val appControl = koinInject<AppControl>()
    val configManager = koinInject<ConfigManager>()
    val crossPasteLogger = koinInject<CrossPasteLogger>()
    val pasteboardService = koinInject<PasteboardService>()
    val settingsViewProvider = koinInject<SettingsViewProvider>()

    val config by configManager.config.collectAsState()

    HighlightedCard(
        modifier =
            Modifier.wrapContentSize(),
        shape = tinyRoundedCornerShape,
        containerColor = AppUIColors.settingsBackground,
    ) {
        LanguageSettingItemView()

        HorizontalDivider(modifier = Modifier.padding(start = 35.dp))

        SettingItemView(
            painter = palette(),
            height = 110.dp,
            text = "theme",
        ) {
            ThemeSegmentedControl()
        }

        HorizontalDivider(modifier = Modifier.padding(start = 35.dp))

        SettingSwitchItemView(
            text = "pasteboard_listening",
            painter = clipboard(),
            getCurrentSwitchValue = { config.enablePasteboardListening },
        ) {
            pasteboardService.toggle()
        }

        HorizontalDivider(modifier = Modifier.padding(start = 35.dp))

        SettingSwitchItemView(
            text = "encrypted_sync",
            painter = shield(),
            getCurrentSwitchValue = { config.enableEncryptSync },
        ) {
            if (appControl.isEncryptionEnabled()) {
                configManager.updateConfig("enableEncryptSync", it)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(start = 35.dp))

        SettingSwitchItemView(
            text = "sound_effect",
            painter = bell(),
            getCurrentSwitchValue = { config.enableSoundEffect },
        ) {
            configManager.updateConfig("enableSoundEffect", it)
        }

        HorizontalDivider(modifier = Modifier.padding(start = 35.dp))

        SettingSwitchItemView(
            text = "launch_at_startup",
            painter = bolt(),
            getCurrentSwitchValue = { config.enableAutoStartUp },
        ) {
            configManager.updateConfig("enableAutoStartUp", it)
        }

        HorizontalDivider(modifier = Modifier.padding(start = 35.dp))

        SettingSwitchItemView(
            text = "debug_mode",
            painter = debug(),
            getCurrentSwitchValue = { config.enableDebugMode },
        ) {
            crossPasteLogger.updateRootLogLevel(
                if (it) "debug" else "info",
            )
        }

        HorizontalDivider(modifier = Modifier.padding(start = 35.dp))

        settingsViewProvider.AboutItemView()
    }
}
