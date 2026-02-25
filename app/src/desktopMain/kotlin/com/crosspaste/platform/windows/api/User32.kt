package com.crosspaste.platform.windows.api

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinNT.HANDLE
import com.sun.jna.win32.StdCallLibrary.StdCallCallback
import com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS

interface User32 : com.sun.jna.platform.win32.User32 {
    fun interface WNDPROC : StdCallCallback {
        fun callback(
            hWnd: HWND?,
            uMsg: Int,
            uParam: WPARAM?,
            lParam: LPARAM?,
        ): Int
    }

    fun SetWindowLongPtr(
        hWnd: HWND?,
        nIndex: Int,
        proc: WNDPROC?,
    ): Int

    fun SetWindowLong(
        hWnd: HWND?,
        nIndex: Int,
        proc: WNDPROC?,
    ): Int

    fun CreateWindowEx(
        styleEx: Int,
        className: String?,
        windowName: String?,
        style: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        hndParent: HWND?,
        hndMenu: Int,
        hndInst: Int,
        param: Any?,
    ): HWND?

    fun SetClipboardViewer(hWndNewViewer: HWND?): HWND?

    fun ChangeClipboardChain(
        hWndRemove: HWND?,
        hWndNewNext: HWND?,
    ): Boolean

    fun MsgWaitForMultipleObjects(
        nCount: Int,
        pHandles: Array<HANDLE?>?,
        bWaitAll: Boolean,
        dwMilliseconds: Int,
        dwWakeMask: Int,
    ): Int

    fun GetClipboardSequenceNumber(): Int

    fun EnumWindows(
        lpEnumFunc: WndEnumProc,
        lParam: Pointer?,
    ): Boolean

    fun IsIconic(hWnd: HWND): Boolean

    fun GetWindowTextLengthW(hWnd: HWND): Int

    fun GetWindowTextW(
        hWnd: HWND,
        lpString: CharArray,
        nMaxCount: Int,
    ): Int

    fun OpenClipboard(hWndNewOwner: HWND?): Boolean

    fun CloseClipboard(): Boolean

    fun EmptyClipboard(): Boolean

    fun SetClipboardData(
        uFormat: Int,
        hMem: HANDLE?,
    ): HANDLE?

    fun GetClipboardData(uFormat: Int): HANDLE?

    fun IsClipboardFormatAvailable(format: Int): Boolean

    fun EnumClipboardFormats(format: Int): Int

    fun RegisterClipboardFormatA(lpszFormat: String): Int

    fun GetClipboardFormatName(
        format: Int,
        lpszFormatName: CharArray,
        cchMaxCount: Int,
    ): Int

    fun GetClassNameW(
        hWnd: HWND,
        lpClassName: CharArray,
        nMaxCount: Int,
    ): Int

    companion object {
        val INSTANCE =
            Native.load(
                "user32",
                User32::class.java,
                DEFAULT_OPTIONS + mapOf("allow-get-last-error" to true),
            ) as User32
        const val GWL_WNDPROC = -4
        const val WM_DESTROY = 0x0002
        const val WM_RENDERFORMAT = 0x0305
        const val WM_RENDERALLFORMATS = 0x0306
        const val WM_CHANGECBCHAIN = 0x030D
        const val WM_DRAWCLIPBOARD = 0x0308

        /**
         * PeekMessage() Options
         */
        const val PM_NOREMOVE = 0x0000
        const val PM_REMOVE = 0x0001
        const val PM_NOYIELD = 0x0002
        const val QS_KEY = 0x0001
        const val QS_MOUSEMOVE = 0x0002
        const val QS_MOUSEBUTTON = 0x0004
        const val QS_POSTMESSAGE = 0x0008
        const val QS_TIMER = 0x0010
        const val QS_PAINT = 0x0020
        const val QS_SENDMESSAGE = 0x0040
        const val QS_HOTKEY = 0x0080
        const val QS_ALLPOSTMESSAGE = 0x0100
        const val QS_RAWINPUT = 0x0400
        const val QS_TOUCH = 0x0800
        const val QS_POINTER = 0x1000
        const val QS_MOUSE = QS_MOUSEMOVE or QS_MOUSEBUTTON
        const val QS_INPUT = QS_MOUSE or QS_KEY or QS_RAWINPUT
        const val QS_ALLEVENTS = QS_INPUT or QS_POSTMESSAGE or QS_TIMER or QS_PAINT or QS_HOTKEY
        const val QS_ALLINPUT = (
            QS_INPUT or QS_POSTMESSAGE or QS_TIMER or QS_PAINT
                or QS_HOTKEY or QS_SENDMESSAGE
        )

        const val EVENT_SYSTEM_FOREGROUND = 0x0003
        const val WINEVENT_OUTOFCONTEXT = 0x0000
    }
}
