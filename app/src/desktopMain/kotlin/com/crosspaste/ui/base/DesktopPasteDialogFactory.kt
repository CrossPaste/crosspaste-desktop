package com.crosspaste.ui.base

import androidx.compose.runtime.Composable

class DesktopPasteDialogFactory : PasteDialogFactory {
    override fun createDialog(
        key: Any,
        title: String,
        onDismissRequest: () -> Unit,
        content: @Composable (() -> Unit),
    ): PasteDialog {
        return DesktopPasteDialog(
            key = key,
            title = title,
            onDismissRequest = onDismissRequest,
            content = content,
        )
    }
}
