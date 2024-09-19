package com.crosspaste.ui.settings

import androidx.compose.runtime.Composable
import com.crosspaste.ui.WindowDecoration

@Composable
actual fun SettingsScreen() {
    WindowDecoration("settings")
    SettingsContentView()
}
