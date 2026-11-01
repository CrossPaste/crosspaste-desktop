package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.User32
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinUser

/**
 * Tells whether the foreground app is full screen, the way a full-screen video or game is,
 * on the monitor showing [isFullscreenOver]'s window. A window counts when it fills its
 * monitor exactly: a maximized one overhangs the work area by its invisible frame instead.
 */
internal class WindowsFullscreenProbe(
    private val user32: User32,
) {
    // The desktop wallpaper windows cover the monitor too
    private val desktopClasses = setOf("Progman", "WorkerW")

    fun isFullscreenOver(window: HWND): Boolean {
        val foreground = user32.GetForegroundWindow() ?: return false
        // A popup owned by the full-screen window (a download bubble, a dialog) may hold
        // the foreground; then judge the window that owns it. The foreground window comes
        // first, as a full-screen window may itself be owned by an ordinary one.
        val monitor =
            fullscreenMonitor(foreground)
                ?: user32
                    .GetAncestor(foreground, WinUser.GA_ROOTOWNER)
                    ?.takeIf { it != foreground }
                    ?.let(::fullscreenMonitor)
                ?: return false
        // Only a full-screen app on the window's own monitor covers it
        return user32.MonitorFromWindow(window, WinUser.MONITOR_DEFAULTTONEAREST) == monitor
    }

    /** The monitor [hwnd] fills exactly, or null when it does not fill one. */
    private fun fullscreenMonitor(hwnd: HWND): WinUser.HMONITOR? {
        if (className(hwnd) in desktopClasses) return null
        val bounds = RECT()
        if (!user32.GetWindowRect(hwnd, bounds)) return null
        val monitor = user32.MonitorFromWindow(hwnd, WinUser.MONITOR_DEFAULTTONEAREST) ?: return null
        val info = WinUser.MONITORINFO()
        if (!user32.GetMonitorInfo(monitor, info).booleanValue()) return null
        return monitor.takeIf { bounds.toRectangle() == info.rcMonitor.toRectangle() }
    }

    private fun className(hwnd: HWND): String {
        val buffer = CharArray(256)
        user32.GetClassNameW(hwnd, buffer, buffer.size)
        return Native.toString(buffer).trim()
    }
}
