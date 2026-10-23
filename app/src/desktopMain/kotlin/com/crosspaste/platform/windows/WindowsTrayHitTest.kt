package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.Shell32
import com.crosspaste.platform.windows.api.User32
import com.crosspaste.platform.windows.api.WndEnumProc
import com.crosspaste.platform.windows.api.windowFromPoint
import com.crosspaste.platform.windows.api.windowProcessId
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinUser.GA_ROOT

/** Adapter for our single ComposeNativeTray 2.1.7 icon (TRAY window, icon id 1000). */
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
        val identifier =
            Shell32.NotifyIconIdentifier().apply {
                hWnd = owner ?: return null
                uID = 1000
            }
        val rect = RECT()
        if (shell32.Shell_NotifyIconGetRect(identifier, rect).toInt() != 0) return null
        return rect.takeIf { it.right > it.left && it.bottom > it.top }
    }
}
