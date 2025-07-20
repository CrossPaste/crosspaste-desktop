package com.crosspaste.platform.windows.api

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.win32.W32APIOptions

internal interface Kernel32 : com.sun.jna.platform.win32.Kernel32 {
    /**
     * Allocate global memory
     * @param uFlags allocation flags
     * @param dwBytes number of bytes
     * @return memory handle
     */
    fun GlobalAlloc(
        uFlags: Int,
        dwBytes: Int,
    ): WinNT.HANDLE?

    /**
     * Lock global memory
     * @param hMem memory handle
     * @return memory pointer
     */
    fun GlobalLock(hMem: WinNT.HANDLE): Pointer?

    /**
     * Unlock global memory
     * @param hMem memory handle
     * @return true if successful
     */
    fun GlobalUnlock(hMem: WinNT.HANDLE): Boolean

    /**
     * Free global memory
     * @param hMem memory handle
     * @return null if successful
     */
    fun GlobalFree(hMem: WinNT.HANDLE): WinNT.HANDLE?

    /**
     * Get global memory size
     * @param hMem memory handle
     * @return memory size
     */
    fun GlobalSize(hMem: WinNT.HANDLE): Int

    /**
     * Get system default ANSI code page
     * @return code page
     */
    fun GetACP(): Int

    /**
     * Get system default OEM code page
     * @return code page
     */
    fun GetOEMCP(): Int

    companion object {
        val INSTANCE: Kernel32 =
            Native.load("kernel32", Kernel32::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}
