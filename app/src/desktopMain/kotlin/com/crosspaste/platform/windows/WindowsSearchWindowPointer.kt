package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.User32
import com.crosspaste.platform.windows.api.windowFromPoint
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser.GA_ROOT
import com.sun.jna.platform.win32.WinUser.GW_OWNER

/** Hit-test native pixels, including owned menus and the editor bubble on any monitor. */
internal class WindowsSearchWindowPointer(
    private val user32: User32,
    private val isTrayIcon: (Int, Int) -> Boolean,
) {
    fun isOutside(
        x: Int,
        y: Int,
        search: HWND?,
        bubble: HWND?,
    ): Boolean {
        if (search == null) return false
        if (isTrayIcon(x, y)) return false
        val hit = user32.windowFromPoint(x, y) ?: return false
        var window: HWND? = user32.GetAncestor(hit, GA_ROOT) ?: return false
        val visited = mutableSetOf<HWND>()
        while (window != null && visited.add(window)) {
            if (window == search || window == bubble) return false
            window = user32.GetWindow(window, DWORD(GW_OWNER.toLong()))
        }
        return true
    }
}
