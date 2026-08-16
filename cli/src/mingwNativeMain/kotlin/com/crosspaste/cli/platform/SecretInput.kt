package com.crosspaste.cli.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.windows.DWORDVar
import platform.windows.ENABLE_ECHO_INPUT
import platform.windows.GetConsoleMode
import platform.windows.GetStdHandle
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.STD_INPUT_HANDLE
import platform.windows.SetConsoleMode

/**
 * Reads one line from stdin with console echo disabled, for pairing codes
 * (design D-P6-3: the code must not be echoed). When stdin is not a console
 * (pipe, redirection) it falls back to a plain read — that case never echoes
 * anyway.
 */
@OptIn(ExperimentalForeignApi::class)
fun readLineNoEcho(): String? =
    memScoped {
        val handle = GetStdHandle(STD_INPUT_HANDLE)
        if (handle == null || handle == INVALID_HANDLE_VALUE) {
            return readlnOrNull()
        }
        val originalMode = alloc<DWORDVar>()
        if (GetConsoleMode(handle, originalMode.ptr) == 0) {
            return readlnOrNull()
        }
        if (SetConsoleMode(handle, originalMode.value and ENABLE_ECHO_INPUT.toUInt().inv()) == 0) {
            return readlnOrNull()
        }
        try {
            readlnOrNull()
        } finally {
            SetConsoleMode(handle, originalMode.value)
        }
    }
