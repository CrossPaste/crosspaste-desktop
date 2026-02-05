package com.crosspaste.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.crosspaste.platform.Platform

@Composable
fun DesktopPasteboardSettingsContentView(platform: Platform) {
    val isWindows = remember { platform.isWindows() }
    if (isWindows) {
        WindowsPasteboardSettingsContentView()
    }
}
