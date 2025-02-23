package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.User32
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinUser.HMONITOR
import com.sun.jna.platform.win32.WinUser.MONITORENUMPROC
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import kotlin.math.max

object WindowDpiHelper {

    interface Shcore : StdCallLibrary {
        fun GetDpiForMonitor(
            hMonitor: HMONITOR?,
            dpiType: Int,
            dpiX: IntByReference,
            dpiY: IntByReference,
        ): Int

        companion object {
            val INSTANCE: Shcore = Native.load("shcore", Shcore::class.java)
        }
    }

    fun getMaxDpiForMonitor(): Int {
        var maxDpi = 0
        val monitorEnumProc =
            MONITORENUMPROC { hMonitor, _, _, _ ->
                val dpiX = IntByReference()
                val dpiY = IntByReference()

                Shcore.INSTANCE.GetDpiForMonitor(hMonitor, 0, dpiX, dpiY)
                val currentDpiX = dpiX.value
                val currentDpiY = dpiY.value
                maxDpi = maxDpi.coerceAtLeast(max(currentDpiX, currentDpiY))

                1 // continue enumeration
            }

        User32.INSTANCE.EnumDisplayMonitors(null, null, monitorEnumProc, LPARAM(0))

        return maxDpi
    }
}
