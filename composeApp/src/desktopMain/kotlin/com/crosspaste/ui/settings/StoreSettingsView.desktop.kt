package com.crosspaste.ui.settings

import androidx.compose.runtime.Composable
import com.crosspaste.ui.base.ExpandView
import com.crosspaste.ui.base.database

@Composable
actual fun StoreSettingsView() {
    ExpandView(
        title = "store",
        icon = { database() },
    ) {
        StoreSettingsContentView()
    }
}
