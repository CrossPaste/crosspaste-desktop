package com.crosspaste.ui

/** Focus protection belongs to one interactive appearance, without a timed grace period. */
internal class MacSearchWindowFocusSession(
    private var gainedFocus: Boolean = false,
) {
    private var active = true
    private var focusGeneration = 0

    @Synchronized
    fun onFocusGained() {
        if (!active) return
        gainedFocus = true
        focusGeneration++
    }

    fun deferFocusLoss(
        enqueue: (() -> Unit) -> Unit,
        shouldDismiss: () -> Boolean,
        dismiss: () -> Unit,
    ) {
        val generation =
            synchronized(this) {
                if (!active || !gainedFocus) return
                focusGeneration
            }
        enqueue {
            synchronized(this) {
                if (active && generation == focusGeneration && shouldDismiss()) {
                    active = false
                    dismiss()
                }
            }
        }
    }

    @Synchronized
    fun close() {
        active = false
    }
}
