package com.crosspaste.platform.windows

import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.platform.win32.WinUser.SIZE
import com.sun.jna.ptr.PointerByReference

class IShellItemImageFactory(pvInstance: Pointer) : Unknown(pvInstance) {
    fun GetImage(
        size: SIZEByValue,
        flags: Int,
        bitmap: PointerByReference,
    ): HRESULT {
        return _invokeNativeObject(
            3,
            arrayOf<Any>(this.getPointer(), size, flags, bitmap),
            HRESULT::class.java,
        ) as HRESULT
    }
}

class SIZEByValue(w: Int, h: Int) : SIZE(w, h), Structure.ByValue
