package com.crosspaste.platform.windows.api

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

internal interface Imm32 : StdCallLibrary {
    fun ImmGetContext(window: HWND): Pointer?

    fun ImmGetCompositionStringW(
        context: Pointer,
        index: Int,
        buffer: Pointer?,
        bufferBytes: Int,
    ): Int

    fun ImmReleaseContext(
        window: HWND,
        context: Pointer,
    ): Boolean

    companion object {
        const val GCS_COMPSTR = 0x0008

        val INSTANCE: Imm32 = Native.load("imm32", Imm32::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}
