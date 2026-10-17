package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.Imm32
import com.crosspaste.platform.windows.api.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser.GUITHREADINFO
import com.sun.jna.ptr.IntByReference

internal class WindowsInputMethod(
    private val user32: User32,
    private val imm32: Imm32,
) {
    /** Sample on mouse press, before committing a candidate can clear its composition. */
    fun isComposing(search: HWND?): Boolean {
        if (search == null || user32.GetForegroundWindow() != search) return false
        val thread = user32.GetWindowThreadProcessId(search, IntByReference())
        if (thread == 0) return false
        val info = GUITHREADINFO().apply { cbSize = size() }
        if (!user32.GetGUIThreadInfo(thread, info)) return false
        val focused = info.hwndFocus?.takeIf { it.pointer != null } ?: return false
        val context = imm32.ImmGetContext(focused) ?: return false
        return try {
            imm32.ImmGetCompositionStringW(context, Imm32.GCS_COMPSTR, null, 0) > 0
        } finally {
            imm32.ImmReleaseContext(focused, context)
        }
    }
}
