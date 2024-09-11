package com.crosspaste.ui.base

import androidx.compose.runtime.mutableStateListOf
import com.crosspaste.utils.createPlatformLock

class DesktopDialogService : DialogService {

    private val lock = createPlatformLock()

    override var dialogs: MutableList<PasteDialog> = mutableStateListOf()

    override fun pushDialog(dialog: PasteDialog) {
        lock.withLock {
            if (!dialogs.map { it.key }.contains(dialog.key)) {
                dialogs.add(dialog)
            }
        }
    }

    override fun popDialog() {
        lock.withLock {
            if (dialogs.isNotEmpty()) {
                dialogs.removeAt(0)
            }
        }
    }
}
