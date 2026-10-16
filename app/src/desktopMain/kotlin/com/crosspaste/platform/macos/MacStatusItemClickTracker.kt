package com.crosspaste.platform.macos

import java.util.concurrent.atomic.AtomicReference

/** One native status-item mouse-down, pending until its asynchronous tray toggle finishes. */
internal class MacStatusItemClickTracker {
    private val pending = AtomicReference<Any?>()

    val pendingClick: Any?
        get() = pending.get()

    fun onInput(isStatusItemClick: Boolean) {
        pending.set(if (isStatusItemClick) Any() else null)
    }

    fun complete(click: Any?) {
        // A delayed callback must not clear a newer click's protection.
        pending.compareAndSet(click, null)
    }
}
