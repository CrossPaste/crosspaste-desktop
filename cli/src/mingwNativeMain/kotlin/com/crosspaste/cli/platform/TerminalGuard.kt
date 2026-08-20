package com.crosspaste.cli.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.windows.DWORD
import platform.windows.DWORDVar
import platform.windows.GetConsoleMode
import platform.windows.GetStdHandle
import platform.windows.STD_INPUT_HANDLE
import platform.windows.STD_OUTPUT_HANDLE
import platform.windows.SetConsoleCtrlHandler
import platform.windows.SetConsoleMode
import platform.windows.TRUE
import platform.windows.WINBOOL
import platform.windows.WriteFile

private var savedStdinMode: DWORD = 0u
private var savedStdoutMode: DWORD = 0u
private var modesSaved = false

/**
 * Restores the console when the process is terminated from outside while a
 * full-screen TUI owns it (console close, external Ctrl-Break, taskkill):
 * puts the saved console modes back, shows the cursor, and leaves the
 * alternate screen. Returning FALSE lets the default handler terminate the
 * process as usual.
 */
@OptIn(ExperimentalForeignApi::class)
fun installTerminalGuard() {
    memScoped {
        val mode = alloc<DWORDVar>()
        var saved = true
        if (GetConsoleMode(GetStdHandle(STD_INPUT_HANDLE), mode.ptr) != 0) {
            savedStdinMode = mode.value
        } else {
            saved = false
        }
        if (GetConsoleMode(GetStdHandle(STD_OUTPUT_HANDLE), mode.ptr) != 0) {
            savedStdoutMode = mode.value
        } else {
            saved = false
        }
        modesSaved = saved
    }
    SetConsoleCtrlHandler(
        staticCFunction { _: DWORD ->
            if (modesSaved) {
                SetConsoleMode(GetStdHandle(STD_INPUT_HANDLE), savedStdinMode)
                SetConsoleMode(GetStdHandle(STD_OUTPUT_HANDLE), savedStdoutMode)
            }
            writeRestoreSequence()
            0 as WINBOOL
        },
        TRUE,
    )
}

/**
 * Puts the console modes saved by [installTerminalGuard] back. Called after
 * leaving raw mode because Mordant 3.0.2's native-Windows setStdinConsoleMode
 * ignores its argument and always writes 0, so its own raw-mode "restore"
 * leaves the console with echo and line input disabled.
 */
@OptIn(ExperimentalForeignApi::class)
fun restoreConsoleModes() {
    if (modesSaved) {
        SetConsoleMode(GetStdHandle(STD_INPUT_HANDLE), savedStdinMode)
        SetConsoleMode(GetStdHandle(STD_OUTPUT_HANDLE), savedStdoutMode)
    }
}

/** Cursor show + alternate-screen exit, written straight to the console. */
@OptIn(ExperimentalForeignApi::class)
private fun writeRestoreSequence() {
    val esc = 27.toByte()
    val sequence =
        byteArrayOf(esc) + "[?25h".encodeToByteArray() +
            byteArrayOf(esc) + "[?1049l".encodeToByteArray()
    memScoped {
        val written = alloc<DWORDVar>()
        sequence.usePinned { pinned ->
            WriteFile(
                GetStdHandle(STD_OUTPUT_HANDLE),
                pinned.addressOf(0),
                sequence.size.convert(),
                written.ptr,
                null,
            )
        }
    }
}
