package com.crosspaste.platform.windows

import com.crosspaste.platform.windows.api.User32
import com.sun.jna.platform.win32.BaseTSD.ULONG_PTR
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.WORD
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.platform.win32.WinUser.INPUT
import com.sun.jna.platform.win32.WinUser.SW_HIDE
import com.sun.jna.platform.win32.WinUser.SW_RESTORE
import com.sun.jna.ptr.IntByReference
import io.github.oshai.kotlinlogging.KotlinLogging

object WindowsFocusUtils {

    private val logger = KotlinLogging.logger {}

    private val user32 = User32.INSTANCE

    @Synchronized
    fun bringToFront(
        prevThreadId: Int?,
        searchWindow: HWND?,
    ) {
        val handle = searchWindow ?: return

        val targetThreadId =
            if (prevThreadId != null) {
                user32.GetWindowThreadProcessId(handle, IntByReference())
            } else {
                0
            }

        val shouldAttach = prevThreadId != null
        if (shouldAttach) {
            val success =
                user32.AttachThreadInput(
                    DWORD(prevThreadId.toLong()),
                    DWORD(targetThreadId.toLong()),
                    true,
                )
            if (!success) {
                logger.error { "AttachThreadInput failed: ${Kernel32.INSTANCE.GetLastError()}" }
                return
            }
        }

        try {
            user32.ShowWindow(handle, SW_RESTORE)
            user32.BringWindowToTop(handle)
            if (user32.SetForegroundWindow(handle)) {
                logger.info { "Foreground window set successfully" }
            } else {
                logger.info { "Failed to set foreground window. Please switch manually" }
            }
        } finally {
            if (shouldAttach) {
                user32.AttachThreadInput(
                    DWORD(prevThreadId.toLong()),
                    DWORD(targetThreadId.toLong()),
                    false,
                )
            }
        }
    }

    fun backToBack(
        backWindow: HWND?,
        previousHwnd: HWND?,
    ) {
        backWindow?.let { hwnd ->
            user32.ShowWindow(hwnd, SW_HIDE)
        }

        previousHwnd?.let { hwnd ->
            user32.ShowWindow(hwnd, WinUser.SW_SHOW)
            user32.SetForegroundWindow(hwnd)
        }
    }

    fun bringToBackAndPaste(
        backWindow: HWND?,
        previousHwnd: HWND?,
        keyCodes: List<Int>,
    ) {
        backToBack(backWindow, previousHwnd)
        paste(keyCodes)
    }

    @Suppress("UNCHECKED_CAST")
    fun paste(keyCodes: List<Int>) {
        if (keyCodes.isEmpty()) {
            return
        }

        val inputs: Array<INPUT> = INPUT().toArray(keyCodes.size) as Array<INPUT>

        for (i in keyCodes.indices) {
            inputs[i].type = DWORD(INPUT.INPUT_KEYBOARD.toLong())
            inputs[i].input.setType(
                "ki",
            )

            // Because setting INPUT_INPUT_KEYBOARD is not enough:
            // https://groups.google.com/d/msg/jna-users/NDBGwC1VZbU/cjYCQ1CjBwAJ
            inputs[i].input.ki.wScan = WORD(0)
            inputs[i].input.ki.time = DWORD(0)
            inputs[i].input.ki.dwExtraInfo = ULONG_PTR(0)

            inputs[i].input.ki.wVk = WORD(keyCodes[i].toLong())
            inputs[i].input.ki.dwFlags = DWORD(0) // keydown
        }

        user32.SendInput(DWORD(inputs.size.toLong()), inputs, inputs[0].size())

        for (i in keyCodes.indices) {
            inputs[i].input.ki.dwFlags = DWORD(2) // keyup
        }

        user32.SendInput(DWORD(inputs.size.toLong()), inputs, inputs[0].size())
    }

    fun findPasteWindow(windowTitle: String): HWND? =
        user32.FindWindow(null, windowTitle)?.also { hwnd ->
            // Set the window icon not to be displayed on the taskbar
            val style =
                user32.GetWindowLong(
                    hwnd,
                    WinUser.GWL_EXSTYLE,
                )
            user32.SetWindowLong(
                hwnd,
                WinUser.GWL_EXSTYLE,
                style or 0x00000080,
            )
        }
}
