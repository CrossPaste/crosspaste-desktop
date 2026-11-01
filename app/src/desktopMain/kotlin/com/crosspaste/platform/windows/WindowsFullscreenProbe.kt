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
        // the foreground; judge the window that owns it
        val root = user32.GetAncestor(foreground, WinUser.GA_ROOTOWNER) ?: foreground
        if (className(root) in desktopClasses) return false

        val bounds = RECT()
        if (!user32.GetWindowRect(root, bounds)) return false
        val monitor = user32.MonitorFromWindow(root, WinUser.MONITOR_DEFAULTTONEAREST) ?: return false
        val info = WinUser.MONITORINFO()
        if (!user32.GetMonitorInfo(monitor, info).booleanValue()) return false
        if (bounds.toRectangle() != info.rcMonitor.toRectangle()) return false
        // Only a full-screen app on the window's own monitor covers it
        return user32.MonitorFromWindow(window, WinUser.MONITOR_DEFAULTTONEAREST) == monitor
    }

    private fun className(hwnd: HWND): String {
        val buffer = CharArray(256)
        user32.GetClassNameW(hwnd, buffer, buffer.size)
        return Native.toString(buffer).trim()
    }
}
