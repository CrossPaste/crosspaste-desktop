package com.clipevery.ui.base

import androidx.compose.runtime.mutableStateListOf
import co.touchlab.stately.concurrency.withLock
import java.util.concurrent.locks.ReentrantLock

class DesktopDialogService : DialogService {

    private val lock = ReentrantLock()

    override var dialogs: MutableList<ClipDialog> = mutableStateListOf()

    override fun pushDialog(dialog: ClipDialog) {
        lock.withLock {
            if (dialogs.map { it.key }.contains(dialog.key)) {
                return
            } else {
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
