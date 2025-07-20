package com.crosspaste.platform.windows.api

import com.sun.jna.Native
import com.sun.jna.win32.W32APIOptions

internal interface Kernel32 : com.sun.jna.platform.win32.Kernel32 {
    fun GetACP(): Int

    companion object {
        val INSTANCE: Kernel32 =
            Native.load("kernel32", Kernel32::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}
