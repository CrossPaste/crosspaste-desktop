package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.compose.koinInject

@Composable
fun DialogView() {
    val dialogService = koinInject<DialogService>()
    val dialog by dialogService.dialogs.collectAsState()

    dialog.firstOrNull()?.content()
}
