package com.crosspaste.platform.windows

import com.sun.jna.Native
import com.sun.jna.platform.win32.WinNT.OSVERSIONINFOEX
import com.sun.jna.win32.StdCallLibrary

object WindowsVersionHelper {

    private interface NtDll : StdCallLibrary {
        fun RtlGetVersion(lpVersionInformation: OSVERSIONINFOEX): Int

        companion object {
            val INSTANCE: NtDll = Native.load("ntdll", NtDll::class.java)
        }
    }

    val isWindows11_22H2OrGreater: Boolean by lazy {
        runCatching {
            val info = OSVERSIONINFOEX()
            if (NtDll.INSTANCE.RtlGetVersion(info) == 0) {
                val major = info.dwMajorVersion.toInt()
                val build = info.dwBuildNumber.toInt()

                major >= 10 && build >= 22621
            } else {
                false
            }
        }.getOrDefault(false)
    }
}
