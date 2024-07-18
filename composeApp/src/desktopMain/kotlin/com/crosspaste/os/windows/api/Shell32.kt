package com.crosspaste.os.windows.api

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid.REFIID
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.W32APIOptions

internal interface Shell32 : com.sun.jna.platform.win32.Shell32 {

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
