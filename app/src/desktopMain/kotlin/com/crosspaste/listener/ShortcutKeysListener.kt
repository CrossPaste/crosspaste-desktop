package com.crosspaste.listener

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

interface ShortcutKeysListener {

    companion object {
        // Safety net only. Normally suppression is lifted the moment the injected paste
        // keystroke is observed released; this timeout just guarantees recognition can't
        // stay disabled forever if that echo is ever dropped by the OS.
        val PASTE_INJECTION_SUPPRESS_TIMEOUT = 500.milliseconds
    }

    var editShortcutKeysMode: Boolean

    var currentKeys: MutableList<KeyboardKey>

    /**
     * Begin ignoring global shortcuts because CrossPaste is about to inject the system
     * paste keystroke (Cmd+V / Ctrl+V) to perform a paste.
     *
     * That synthetic keystroke is delivered back to this global listener; if a paste
     * shortcut is bound to a colliding combination it would re-trigger the paste
     * endlessly, causing an infinite paste loop (issue #4500). Suppression is lifted as
     * soon as the injected keystroke is observed released (i.e. the simulation actually
     * finished), or after [timeout] as a safety net.
     */
    fun beginPasteSuppression(timeout: Duration)
}
