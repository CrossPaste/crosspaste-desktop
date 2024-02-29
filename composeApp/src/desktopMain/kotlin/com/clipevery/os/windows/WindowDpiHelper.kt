package com.clipevery.os.windows

import com.clipevery.os.windows.api.User32
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinUser.HMONITOR
import com.sun.jna.platform.win32.WinUser.MONITORENUMPROC
import com.sun.jna.win32.StdCallLibrary
import kotlin.math.max


object WindowDpiHelper {

    interface Shcore : StdCallLibrary {
        fun GetDpiForMonitor(hMonitor: HMONITOR?, dpiType: Int, dpiX: Pointer?, dpiY: Pointer?): Int

        companion object {
            val INSTANCE: Shcore = Native.load("shcore", Shcore::class.java)
        }
    }

    fun getMaxDpiForMonitor(): Int {
        var maxDpi = 0;
        val monitorEnumProc: MONITORENUMPROC = object : MONITORENUMPROC {
            override fun apply(hMonitor: HMONITOR?, hdcMonitor: WinDef.HDC?, lprcMonitor: RECT?, dwData: LPARAM?): Int {
                val dpiX = Pointer(0)
                val dpiY = Pointer(0)

                Shcore.INSTANCE.GetDpiForMonitor(hMonitor, 0,  /* MDT_EFFECTIVE_DPI */dpiX, dpiY)
                val currentDpiX = dpiX.getInt(0)
                val currentDpiY = dpiY.getInt(0)

                maxDpi = Math.max(maxDpi, max(currentDpiX, currentDpiY))

                return 1 // continue enumeration
            }


        }

        User32.INSTANCE.EnumDisplayMonitors(null, null, monitorEnumProc, LPARAM(0))

        return maxDpi
    }

}