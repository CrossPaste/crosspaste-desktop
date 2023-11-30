package com.clipevery.windows.api

import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef.HWND

import com.sun.jna.win32.StdCallLibrary


interface Dwmapi : StdCallLibrary {
    fun DwmExtendFrameIntoClientArea(hwnd: HWND?, margins: MARGINS?): Int

    companion object {
        val INSTANCE = Native.load("dwmapi", Dwmapi::class.java) as Dwmapi
    }
}
