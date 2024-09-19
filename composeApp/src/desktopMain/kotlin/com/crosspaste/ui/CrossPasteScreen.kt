package com.crosspaste.ui

import androidx.compose.runtime.Composable
import com.crosspaste.ui.devices.DeviceDetailScreen
import com.crosspaste.ui.paste.edit.PasteTextEditScreen
import com.crosspaste.ui.settings.SettingsScreen
import com.crosspaste.ui.settings.ShortcutKeysScreen

@Composable
fun CrossPasteScreen() {
    val currentScreenContext = LocalPageViewContent.current

    when (currentScreenContext.value.screenType) {
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
    }
}
