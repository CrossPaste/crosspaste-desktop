package com.crosspaste.ui

/** A status item can take key-window focus while the application remains active. */
internal data class MacSearchWindowFocusState(
    val appActive: Boolean,
    val searchFocused: Boolean,
    val bubbleFocused: Boolean,
    val mainFocused: Boolean,
    // The tray toggle handles a status item click; dismissing first would make it reopen.
    val pointerOnStatusItem: Boolean = false,
) {
    val shouldDismiss: Boolean
        get() = !pointerOnStatusItem && !searchFocused && !bubbleFocused && (!appActive || mainFocused)
}

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
        isCurrentAppearance: () -> Boolean = { true },
        dismiss: () -> Unit,
    ) {
        val generation =
            synchronized(this) {
                if (!active || !gainedFocus) return
                focusGeneration
            }
        enqueue {
            synchronized(this) {
                if (active && generation == focusGeneration && isCurrentAppearance() && shouldDismiss()) {
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
