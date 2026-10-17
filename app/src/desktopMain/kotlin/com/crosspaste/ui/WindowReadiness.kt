package com.crosspaste.ui

import com.crosspaste.utils.mainDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.awt.EventQueue
import java.awt.Window
import java.awt.event.HierarchyListener
import kotlin.coroutines.resume

/** Wait for the native peer and visibility, rather than guessing with a startup timer. */
internal suspend fun awaitWindowShowing(window: Window) =
    withContext(mainDispatcher) {
        if (window.isShowing) return@withContext
        suspendCancellableCoroutine<Unit> { continuation ->
            val listener =
                object : HierarchyListener {
                    override fun hierarchyChanged(event: java.awt.event.HierarchyEvent) {
                        if (window.isShowing) {
                            window.removeHierarchyListener(this)
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    }
                }
            window.addHierarchyListener(listener)
            continuation.invokeOnCancellation {
                EventQueue.invokeLater { window.removeHierarchyListener(listener) }
            }
            if (window.isShowing) {
                window.removeHierarchyListener(listener)
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }
