package com.crosspaste.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.crosspaste.ui.base.ExpandView
import com.crosspaste.ui.base.clipboard

@Composable
actual fun PasteboardSettingsView() {
    ExpandView(
        title = "pasteboard",
        icon = { clipboard() },
        iconTintColor = Color(0xFF007AFF),
    ) {
        PasteboardSettingsContentView()
    }
}
