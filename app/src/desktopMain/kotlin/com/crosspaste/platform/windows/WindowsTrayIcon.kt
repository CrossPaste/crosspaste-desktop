package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.User32
import com.crosspaste.platform.windows.api.WndEnumProc
import com.crosspaste.platform.windows.api.windowProcessId
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND

/** Our single ComposeNativeTray 2.1.7 icon is owned by a "TRAY" window and has icon id 1000. */
internal const val TRAY_ICON_ID = 1000

/** The window of [processId] that owns its tray icon, or null when no icon is shown. */
internal fun findTrayIconOwner(
    user32: User32,
    processId: Int,
): HWND? {
    var owner: HWND? = null
    user32.EnumWindows(
        object : WndEnumProc {
            override fun callback(
                hWnd: HWND,
                lParam: Pointer?,
            ): Boolean {
                if (user32.windowProcessId(hWnd) != processId) return true
                val name = CharArray(32)
                if (user32.GetClassNameW(hWnd, name, name.size) == 0 || Native.toString(name) != "TRAY") return true
                owner = hWnd
                return false
            }
        },
        null,
    )
    return owner
}
