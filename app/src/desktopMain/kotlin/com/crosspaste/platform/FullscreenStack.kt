package com.crosspaste.platform

/**
 * A window on the paste panel button's display, as seen while walking the window stack
 * from front to back. [ordinary] is false for windows that float above others without
 * hiding them: always-on-top overlays and popups owned by another window (a download
 * bubble over a full-screen video).
 */
internal data class StackedWindow(
    val fullscreen: Boolean,
    val ordinary: Boolean,
)

/**
 * Whether the display shows a full-screen app, judged by its windows from front to back.
 * Focus plays no part: a full-screen video keeps playing on one display while the user
 * works on another. Overlays and owned popups above it are looked past, while an ordinary
 * window in front means the user put something over it.
 */
internal fun isFullscreenOnTop(windows: Sequence<StackedWindow>): Boolean {
    for (window in windows) {
        if (window.fullscreen) return true
        if (window.ordinary) return false
    }
    return false
}
