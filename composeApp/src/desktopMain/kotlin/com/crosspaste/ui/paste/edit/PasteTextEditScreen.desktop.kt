package com.crosspaste.ui.paste.edit

import androidx.compose.runtime.Composable
import com.crosspaste.ui.WindowDecoration

@Composable
actual fun PasteTextEditScreen() {
    WindowDecoration("text_edit")
    PasteTextEditContentView()
}
