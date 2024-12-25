package com.crosspaste.ui.settings

import androidx.compose.runtime.Composable
import com.crosspaste.ui.base.ExpandView
import com.crosspaste.ui.base.clipboard

@Composable
actual fun PasteboardSettingsView() {
    ExpandView(
        title = "pasteboard",
        icon = { clipboard() },
    ) {
        PasteboardSettingsContentView()
    }
}
