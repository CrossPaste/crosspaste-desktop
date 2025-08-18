package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.crosspaste.app.AppControl
import com.crosspaste.app.DesktopAppLaunchState
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.log.CrossPasteLogger
import com.crosspaste.paste.PasteboardService
import com.crosspaste.ui.base.HighlightedCard
import com.crosspaste.ui.base.bell
import com.crosspaste.ui.base.bolt
import com.crosspaste.ui.base.clipboard
import com.crosspaste.ui.base.debug
import com.crosspaste.ui.base.layout
import com.crosspaste.ui.base.palette
import com.crosspaste.ui.base.shield
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.xxxLarge
import com.crosspaste.ui.theme.DesktopSearchWindowStyle
import org.koin.compose.koinInject

@Composable
fun MainSettingsContentView() {
    val appLaunchState = koinInject<DesktopAppLaunchState>()
    val appControl = koinInject<AppControl>()
    val configManager = koinInject<DesktopConfigManager>()
    val crossPasteLogger = koinInject<CrossPasteLogger>()
    val pasteboardService = koinInject<PasteboardService>()
    val settingsViewProvider = koinInject<SettingsViewProvider>()

    val config by configManager.config.collectAsState()

    if (!appLaunchState.accessibilityPermissions) {
        GrantAccessibilityView()
        Spacer(modifier = Modifier.height(medium))
    }

    HighlightedCard(
        modifier =
            Modifier.wrapContentSize(),
        shape = tinyRoundedCornerShape,
        colors =
            CardDefaults.cardColors(
                containerColor = AppUIColors.generalBackground,
            ),
    ) {
        LanguageSettingItemView()

        HorizontalDivider(modifier = Modifier.padding(start = xxxLarge))

        FontSettingItemView()

        HorizontalDivider(modifier = Modifier.padding(start = xxxLarge))

        SettingItemView(
            painter = palette(),
            height = xxLarge * 3 + tiny2X * 4,
            text = "theme",
        ) {
            ThemeSegmentedControl()
        }

        HorizontalDivider(modifier = Modifier.padding(start = xxxLarge))

        SettingSwitchItemView(
            text = "pasteboard_listening",
            painter = clipboard(),
            getCurrentSwitchValue = { config.enablePasteboardListening },
        ) {
            pasteboardService.toggle()
        }

        HorizontalDivider(modifier = Modifier.padding(start = xxxLarge))

        SettingSwitchItemView(
            text = "encrypted_sync",
            painter = shield(),
            getCurrentSwitchValue = { config.enableEncryptSync },
        ) {
            if (appControl.isEncryptionEnabled()) {
                configManager.updateConfig("enableEncryptSync", it)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(start = xxxLarge))

        SettingSwitchItemView(
            text = "sound_effect",
            painter = bell(),
            getCurrentSwitchValue = { config.enableSoundEffect },
        ) {
            configManager.updateConfig("enableSoundEffect", it)
        }

        HorizontalDivider(modifier = Modifier.padding(start = xxxLarge))

        SettingSingleChoiceItemView(
            text = "window_style",
            painter = layout(),
            modes =
                listOf(
                    DesktopSearchWindowStyle.SIDE_STYLE.style,
                    DesktopSearchWindowStyle.CENTER_STYLE.style,
                ),
            getCurrentSingleChoiceValue = {
                config.searchWindowStyle
            },
        ) {
            configManager.updateConfig("searchWindowStyle", it)
        }

        HorizontalDivider(modifier = Modifier.padding(start = xxxLarge))

        SettingSwitchItemView(
            text = "launch_at_startup",
            painter = bolt(),
            getCurrentSwitchValue = { config.enableAutoStartUp },
        ) {
            configManager.updateConfig("enableAutoStartUp", it)
        }

        HorizontalDivider(modifier = Modifier.padding(start = xxxLarge))

        SettingSwitchItemView(
            text = "debug_mode",
            painter = debug(),
            getCurrentSwitchValue = { config.enableDebugMode },
        ) {
            crossPasteLogger.updateRootLogLevel(
                if (it) "debug" else "info",
            )
        }

        HorizontalDivider(modifier = Modifier.padding(start = xxxLarge))

        settingsViewProvider.AboutItemView()
    }
}
