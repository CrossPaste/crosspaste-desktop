package com.clipevery.os.windows.api

import com.sun.jna.Native
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS

interface Kernel32 : StdCallLibrary {
    fun GetCurrentThreadId(): Int

    companion object {
        val INSTANCE =
            Native.load("kernel32", Kernel32::class.java, DEFAULT_OPTIONS) as Kernel32
    }
}
