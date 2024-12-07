package com.crosspaste.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.crosspaste.ui.base.ExpandView
import com.crosspaste.ui.base.network

@Composable
actual fun NetSettingsView() {
    ExpandView(
        title = "network",
        icon = { network() },
        iconTintColor = Color(0xFFFFC94A),
    ) {
        NetSettingsContentView()
    }
}
