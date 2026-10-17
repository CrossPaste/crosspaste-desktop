package com.crosspaste.ui

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Windows activation and deferred dismissal belong to one appearance of the window. */
internal class SearchWindowFocusSession {
    private var active = true
    private var gainedFocus = false
    private var activationCompleted = false
    private var focusGeneration = 0
    private var activation: Job? = null

    suspend fun activate(activate: suspend () -> Boolean) =
        coroutineScope {
            val job =
                launch(start = CoroutineStart.LAZY) {
                    val foreground = activate()
                    synchronized(this@SearchWindowFocusSession) {
                        if (active) {
                            activationCompleted = true
                            gainedFocus = foreground
                            if (foreground) onFocusGained(true)
                        }
                    }
                }
            synchronized(this@SearchWindowFocusSession) {
                if (active) {
                    activation = job
                    job.start()
                } else {
                    job.cancel()
                }
            }
            job.join()
        }

    @Synchronized
    fun onFocusGained(isForeground: Boolean) {
        // A brief automatic acquisition can be followed by AWT restoring the old app.
        // It must not cancel the explicit activation that finishes opening this window.
        if (!active || !isForeground) return
        gainedFocus = true
        focusGeneration++
    }

    fun onForegroundChanged(
        isForeground: Boolean,
        onFocusLost: () -> Unit,
    ) {
        if (isForeground) {
            onFocusGained(true)
        } else if (synchronized(this) { active && gainedFocus && activationCompleted }) {
            onFocusLost()
        }
    }

    fun deferFocusLoss(
        enqueue: (() -> Unit) -> Unit,
        shouldDismiss: () -> Boolean,
        dismiss: () -> Unit,
    ) {
        val generation = synchronized(this) { focusGeneration }
        enqueue {
            synchronized(this) {
                if (active && activationCompleted && gainedFocus && generation == focusGeneration && shouldDismiss()) {
                    close()
                    dismiss()
                }
            }
        }
    }

    /** A real outside press expresses dismissal even if Windows never granted foreground. */
    fun deferOutsidePress(
        enqueue: (() -> Unit) -> Unit,
        shouldDismiss: () -> Boolean,
        dismiss: () -> Unit,
    ) {
        enqueue {
            synchronized(this) {
                if (active && shouldDismiss()) {
                    close()
                    dismiss()
                }
            }
        }
    }

    @Synchronized
    fun close() {
        active = false
        activation?.cancel()
    }
}
