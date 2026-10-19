package com.crosspaste.platform.windows.api

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.platform.win32.Guid.REFIID
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.W32APIOptions

internal interface Shell32 : com.sun.jna.platform.win32.Shell32 {

    fun Shell_NotifyIconGetRect(
        identifier: NotifyIconIdentifier,
        rect: RECT,
    ): HRESULT

    @Structure.FieldOrder("cbSize", "hWnd", "uID", "guidItem")
    class NotifyIconIdentifier : Structure() {
        @JvmField var cbSize = 0

        @JvmField var hWnd: HWND? = null

        @JvmField var uID = 0

        @JvmField var guidItem = GUID()

        init {
            cbSize = size()
        }
    }

    fun SHCreateItemFromParsingName(
        path: WString,
        pointer: Pointer?,
        guid: REFIID,
        reference: PointerByReference,
    ): HRESULT?

    companion object {
        val INSTANCE: Shell32 =
            Native.load("shell32", Shell32::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}
