package com.crosspaste.platform.windows

import com.crosspaste.platform.StackedWindow
import com.crosspaste.platform.isFullscreenOnTop
import com.crosspaste.platform.windows.api.Dwmapi
import com.crosspaste.platform.windows.api.User32
import com.crosspaste.platform.windows.api.WndEnumProc
import com.crosspaste.platform.windows.api.windowProcessId
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import java.awt.Rectangle

/**
 * Tells whether a full-screen app, such as a full-screen video or game, is on the monitor
 * showing a given window, whichever app has the focus. A window counts as full screen when
 * it fills its monitor from inside, see [fillsMonitor].
 */
internal class WindowsFullscreenProbe(
    private val user32: User32,
    private val dwmapi: Dwmapi,
    private val processId: Int,
) {
    // The desktop wallpaper windows cover the monitor too
    private val desktopClasses = setOf("Progman", "WorkerW")

    fun isFullscreenOver(window: HWND): Boolean {
        val monitor = user32.MonitorFromWindow(window, WinUser.MONITOR_DEFAULTTONEAREST) ?: return false
        val info = WinUser.MONITORINFO()
        if (!user32.GetMonitorInfo(monitor, info).booleanValue()) return false
        val monitorBounds = info.rcMonitor.toRectangle()

        return isFullscreenOnTop(
            windowsFrontToBack()
                .asSequence()
                .filter(::isShownWindow)
                .mapNotNull { hwnd -> bounds(hwnd)?.let { hwnd to it } }
                .filter { (_, bounds) -> bounds.intersects(monitorBounds) }
                .map { (hwnd, bounds) ->
                    StackedWindow(fullscreen = fillsMonitor(bounds, monitorBounds), ordinary = isOrdinary(hwnd))
                },
        )
    }

    // EnumWindows walks the top-level windows in z-order, topmost ones first
    private fun windowsFrontToBack(): List<HWND> {
        val windows = mutableListOf<HWND>()
        user32.EnumWindows(
            object : WndEnumProc {
                override fun callback(
                    hWnd: HWND,
                    lParam: Pointer?,
                ): Boolean {
                    windows += hWnd
                    return true
                }
            },
            null,
        )
        return windows
    }

    // Chromium browsers keep a full-screen window one pixel short of the monitor at times,
    // so a pixel of slack is allowed. A maximized window overhangs the monitor by its
    // invisible frame and never stays inside it.
    private fun fillsMonitor(
        window: Rectangle,
        monitor: Rectangle,
    ): Boolean = monitor.contains(window) && window.width >= monitor.width - 1 && window.height >= monitor.height - 1

    private fun isShownWindow(hwnd: HWND): Boolean {
        if (!user32.IsWindowVisible(hwnd) || user32.IsIconic(hwnd)) return false
        // Our own windows, the paste panel button above all, never hide it
        if (user32.windowProcessId(hwnd) == processId) return false
        if (user32.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE) and WS_EX_TOOLWINDOW != 0) return false
        if (isCloaked(hwnd)) return false
        return className(hwnd) !in desktopClasses
    }

    private fun isOrdinary(hwnd: HWND): Boolean {
        val topmost = user32.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE) and WS_EX_TOPMOST != 0
        val owned = user32.GetWindow(hwnd, DWORD(WinUser.GW_OWNER.toLong())) != null
        return !topmost && !owned
    }

    private fun isCloaked(hwnd: HWND): Boolean {
        val cloaked = IntByReference()
        return dwmapi.DwmGetWindowAttribute(hwnd, Dwmapi.DWMWA_CLOAKED, cloaked, Int.SIZE_BYTES) == 0 &&
            cloaked.value != 0
    }

    private fun bounds(hwnd: HWND): Rectangle? {
        val rect = RECT()
        return if (user32.GetWindowRect(hwnd, rect)) rect.toRectangle() else null
    }

    private fun className(hwnd: HWND): String {
        val buffer = CharArray(256)
        user32.GetClassNameW(hwnd, buffer, buffer.size)
        return Native.toString(buffer).trim()
    }

    companion object {
        private const val WS_EX_TOOLWINDOW = 0x00000080
        private const val WS_EX_TOPMOST = 0x00000008
    }
}
