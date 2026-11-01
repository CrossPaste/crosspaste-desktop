package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.User32
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinUser

/**
 * Tells whether the foreground window fills its whole monitor, the way a full-screen video
 * or game does. A maximized window stops at the work area or overhangs the monitor by its
 * frame, so only an exact match counts.
 */
internal class WindowsFullscreenProbe(
    private val user32: User32,
) {
    // The desktop wallpaper windows cover the monitor too
    private val desktopClasses = setOf("Progman", "WorkerW")

    fun isForegroundFullscreen(): Boolean {
        val hwnd = user32.GetForegroundWindow() ?: return false
        if (className(hwnd) in desktopClasses) return false

        val window = RECT()
        if (!user32.GetWindowRect(hwnd, window)) return false
        val monitor = user32.MonitorFromWindow(hwnd, WinUser.MONITOR_DEFAULTTONEAREST) ?: return false
        val info = WinUser.MONITORINFO()
        if (!user32.GetMonitorInfo(monitor, info).booleanValue()) return false
        return window.toRectangle() == info.rcMonitor.toRectangle()
    }

    private fun className(hwnd: HWND): String {
        val buffer = CharArray(256)
        user32.GetClassNameW(hwnd, buffer, buffer.size)
        return Native.toString(buffer).trim()
    }
}
