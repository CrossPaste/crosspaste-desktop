package com.crosspaste.ui.base

import androidx.compose.runtime.Composable

interface PasteDialogFactory {

    fun createDialog(
        key: Any,
        title: String,
        onDismissRequest: () -> Unit = { DialogService.popDialog() },
        content: @Composable () -> Unit,
    ): PasteDialog
}
