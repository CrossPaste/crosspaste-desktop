package com.crosspaste.ui.settings

import androidx.compose.runtime.Composable
import com.crosspaste.ui.base.ExpandView

@Composable
actual fun NetSettingsView() {
    ExpandView("network") {
        NetSettingsContentView()
    }
}
