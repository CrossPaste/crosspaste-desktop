package com.crosspaste.cli.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.ECHO
import platform.posix.STDIN_FILENO
import platform.posix.TCSAFLUSH
import platform.posix.tcgetattr
import platform.posix.tcsetattr
import platform.posix.termios

/**
 * Reads one line from stdin with terminal echo disabled, for pairing codes
 * (design D-P6-3: the code must not be echoed). When stdin is not a
 * configurable terminal (pipe, closed fd) it falls back to a plain read —
 * that case never echoes anyway.
 */
@OptIn(ExperimentalForeignApi::class)
fun readLineNoEcho(): String? =
    memScoped {
        val original = alloc<termios>()
        if (tcgetattr(STDIN_FILENO, original.ptr) != 0) {
            return readlnOrNull()
        }
        val modified = alloc<termios>()
        tcgetattr(STDIN_FILENO, modified.ptr)
        // tcflag_t is UInt on Linux and ULong on macOS; widen, mask, convert back
        val flags: ULong = modified.c_lflag.convert()
        modified.c_lflag = (flags and ECHO.toULong().inv()).convert()
        if (tcsetattr(STDIN_FILENO, TCSAFLUSH, modified.ptr) != 0) {
            return readlnOrNull()
        }
        try {
            readlnOrNull()
        } finally {
            tcsetattr(STDIN_FILENO, TCSAFLUSH, original.ptr)
        }
    }
