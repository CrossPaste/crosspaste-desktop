package com.crosspaste.ui.base

import androidx.compose.runtime.mutableStateListOf
import com.crosspaste.utils.createPlatformLock

object DialogService {

    private val lock = createPlatformLock()

    var dialogs: MutableList<PasteDialog> = mutableStateListOf()

    fun pushDialog(dialog: PasteDialog) {
        lock.withLock {
            if (!dialogs.map { it.key }.contains(dialog.key)) {
                dialogs.add(dialog)
            }
        }
    }

    fun popDialog() {
        lock.withLock {
            if (dialogs.isNotEmpty()) {
                dialogs.removeAt(0)
            }
        }
    }
}
