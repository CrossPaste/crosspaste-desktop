package com.crosspaste.platform.windows.api

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.win32.StdCallLibrary
import kotlin.jvm.java

interface Dwmapi : StdCallLibrary {
    fun DwmSetWindowAttribute(
        hwnd: WinDef.HWND,
        dwAttribute: Int,
        pvAttribute: Pointer,
        cbAttribute: Int,
    ): Int

    companion object {
        val INSTANCE: Dwmapi = Native.load("dwmapi", Dwmapi::class.java)

        const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
        const val DWMWA_SYSTEMBACKDROP_TYPE = 38

        const val DWMSBT_AUTO = 0
        const val DWMSBT_NONE = 1
        const val DWMSBT_MICA = 2
        const val DWMSBT_TRANSIENT = 3
        const val DWMSBT_TABBED = 4
    }
}
