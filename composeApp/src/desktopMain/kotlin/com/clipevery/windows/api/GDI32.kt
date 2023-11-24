package com.clipevery.windows.api

import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef.HRGN

import com.sun.jna.win32.StdCallLibrary


interface GDI32 : StdCallLibrary {
    fun CreateRoundRectRgn(x1: Int, y1: Int, x2: Int, y2: Int, w: Int, h: Int): HRGN?

    companion object {
        val INSTANCE: GDI32 = Native.load("gdi32", GDI32::class.java)
    }
}