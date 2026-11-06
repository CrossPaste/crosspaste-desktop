package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.Shell32
import com.crosspaste.platform.windows.api.User32
import com.crosspaste.platform.windows.api.windowFromPoint
import com.crosspaste.platform.windows.api.windowProcessId
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinUser.GA_ROOT

/** Hit testing against our tray icon, see [findTrayIconOwner]. */
internal class WindowsTrayHitTest(
    private val user32: User32,
    private val shell32: Shell32,
    private val processId: Int,
) {
    fun contains(
        x: Int,
        y: Int,
    ): Boolean {
        val rect = iconRect() ?: return false
        return x >= rect.left && x < rect.right && y >= rect.top && y < rect.bottom
    }

    /** Shell focus during tray interaction is handled by mouse presses and tray actions. */
    fun ownsForeground(foreground: HWND): Boolean {
        val rect = iconRect() ?: return false
        val hit =
            user32.windowFromPoint((rect.left + rect.right) / 2, (rect.top + rect.bottom) / 2)
                ?: return false
        val root = user32.GetAncestor(hit, GA_ROOT) ?: return false
        if (root != foreground) return false
        val taskbar = user32.FindWindow("Shell_TrayWnd", null) ?: return false
        val shellProcess = user32.windowProcessId(taskbar) ?: return false
        return user32.windowProcessId(root) == shellProcess
    }

    private fun iconRect(): RECT? {
        val owner = findTrayIconOwner(user32, processId)
        val identifier =
            Shell32.NotifyIconIdentifier().apply {
                hWnd = owner ?: return null
                uID = TRAY_ICON_ID
            }
        val rect = RECT()
        if (shell32.Shell_NotifyIconGetRect(identifier, rect).toInt() != 0) return null
        return rect.takeIf { it.right > it.left && it.bottom > it.top }
    }
}
