package com.crosspaste.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.crosspaste.app.AppWindowManager
import com.crosspaste.ui.devices.DeviceDetailScreen
import com.crosspaste.ui.paste.edit.PasteTextEditScreen
import com.crosspaste.ui.settings.SettingsScreen
import com.crosspaste.ui.settings.ShortcutKeysScreen
import org.koin.compose.koinInject

@Composable
actual fun CrossPasteScreen() {
    val appWindowManager = koinInject<AppWindowManager>()

    val screen by appWindowManager.screenContext.collectAsState()

    when (screen.screenType) {
        ScreenType.PASTE_PREVIEW,
        ScreenType.DEVICES,
        ScreenType.QR_CODE,
        ScreenType.DEBUG,
        -> {
            HomeScreen()
        }
        ScreenType.SETTINGS -> {
            SettingsScreen()
        }
        ScreenType.SHORTCUT_KEYS -> {
            ShortcutKeysScreen()
        }
        ScreenType.ABOUT -> {
            AboutScreen()
        }
        ScreenType.DEVICE_DETAIL -> {
            DeviceDetailScreen()
        }
        ScreenType.PASTE_TEXT_EDIT -> {
            PasteTextEditScreen()
        }
        else -> {}
    }
}
