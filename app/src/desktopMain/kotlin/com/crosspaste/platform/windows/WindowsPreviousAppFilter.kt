package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.User32
import com.crosspaste.platform.windows.api.windowProcessId
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef.HWND

/** Decides whether a newly focused window counts as the app to paste back into. */
internal class WindowsPreviousAppFilter(
    private val user32: User32,
    private val processId: Int,
) {
    private val ignoredClasses = setOf("Shell_TrayWnd", "Shell_SecondaryTrayWnd")

    fun accepts(hwnd: HWND): Boolean {
        if (!user32.IsWindowVisible(hwnd)) return false
        // None of our own windows (main, search, bubble editor, ...) is a paste target
        if (user32.windowProcessId(hwnd) == processId) return false
        return className(hwnd) !in ignoredClasses
    }

    fun className(hwnd: HWND): String {
        val buffer = CharArray(512)
        user32.GetClassNameW(hwnd, buffer, buffer.size)
        return Native.toString(buffer).trim()
    }
}
