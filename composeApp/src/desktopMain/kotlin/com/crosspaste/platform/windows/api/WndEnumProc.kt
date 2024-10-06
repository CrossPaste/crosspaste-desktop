package com.crosspaste.platform.windows.api

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.win32.StdCallLibrary

interface WndEnumProc : StdCallLibrary.StdCallCallback {
    fun callback(
        hWnd: WinDef.HWND,
        lParam: Pointer?,
    ): Boolean
}
