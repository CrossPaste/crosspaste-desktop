package com.crosspaste.ui.base

import com.crosspaste.utils.createPlatformLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DialogService {

    private val lock = createPlatformLock()

    private val _dialogs = MutableStateFlow<List<PasteDialog>>(listOf())

    val dialogs: StateFlow<List<PasteDialog>> = _dialogs.asStateFlow()

    fun pushDialog(dialog: PasteDialog) {
        lock.withLock {
            if (!dialogs.value.map { it.key }.contains(dialog.key)) {
                _dialogs.value += dialog
            }
        }
    }

    fun popDialog() {
        lock.withLock {
            if (dialogs.value.isNotEmpty()) {
                _dialogs.value = dialogs.value.drop(1)
            }
        }
    }
}
