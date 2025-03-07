package com.crosspaste.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.crosspaste.config.ConfigManager
import com.crosspaste.log.CrossPasteLogger
import com.crosspaste.paste.PasteboardService
import com.crosspaste.ui.base.bell
import com.crosspaste.ui.base.bolt
import com.crosspaste.ui.base.clipboard
import com.crosspaste.ui.base.debug
import com.crosspaste.ui.base.palette
import com.crosspaste.ui.base.shield
import org.koin.compose.koinInject

@Composable
fun MainSettingsContentView() {
    val configManager = koinInject<ConfigManager>()
    val crossPasteLogger = koinInject<CrossPasteLogger>()
    val pasteboardService = koinInject<PasteboardService>()
    val settingsViewProvider = koinInject<SettingsViewProvider>()

    Column(
        modifier =
            Modifier.wrapContentSize()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
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
            getCurrentSwitchValue = { configManager.config.enablePasteboardListening },
        ) {
            pasteboardService.toggle()
        }

        HorizontalDivider(modifier = Modifier.padding(start = 35.dp))

        SettingSwitchItemView(
            text = "encrypted_sync",
            painter = shield(),
            getCurrentSwitchValue = { configManager.config.enableEncryptSync },
        ) {
            configManager.updateConfig("enableEncryptSync", it)
        }

        HorizontalDivider(modifier = Modifier.padding(start = 35.dp))

        SettingSwitchItemView(
            text = "sound_effect",
            painter = bell(),
            getCurrentSwitchValue = { configManager.config.enableSoundEffect },
        ) {
            configManager.updateConfig("enableSoundEffect", it)
        }

        HorizontalDivider(modifier = Modifier.padding(start = 35.dp))

        SettingSwitchItemView(
            text = "launch_at_startup",
            painter = bolt(),
            getCurrentSwitchValue = { configManager.config.enableAutoStartUp },
        ) {
            configManager.updateConfig("enableAutoStartUp", it)
        }

        HorizontalDivider(modifier = Modifier.padding(start = 35.dp))

        SettingSwitchItemView(
            text = "debug_mode",
            painter = debug(),
            getCurrentSwitchValue = { configManager.config.enableDebugMode },
        ) {
            crossPasteLogger.updateRootLogLevel(
                if (it) "debug" else "info",
            )
        }

        HorizontalDivider(modifier = Modifier.padding(start = 35.dp))

        settingsViewProvider.AboutItemView()
    }
}
