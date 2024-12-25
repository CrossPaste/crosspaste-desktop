package com.crosspaste.ui.settings

import androidx.compose.runtime.Composable
import com.crosspaste.ui.base.ExpandView
import com.crosspaste.ui.base.network

@Composable
actual fun NetSettingsView() {
    ExpandView(
        title = "network",
        icon = { network() },
    ) {
        NetSettingsContentView()
    }
}
