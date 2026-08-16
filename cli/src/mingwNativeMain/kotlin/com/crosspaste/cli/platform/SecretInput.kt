package com.crosspaste.cli.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.windows.DWORDVar
import platform.windows.GetConsoleMode
import platform.windows.GetStdHandle
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.STD_INPUT_HANDLE
import secretinput.read_console_char_no_echo

/** Reads a line one character at a time without changing shared console mode. */
@OptIn(ExperimentalForeignApi::class)
fun readLineNoEcho(): String? =
    memScoped {
        val handle = GetStdHandle(STD_INPUT_HANDLE)
        val mode = alloc<DWORDVar>()
        val isConsole =
            handle != null &&
                handle != INVALID_HANDLE_VALUE &&
                GetConsoleMode(handle, mode.ptr) != 0
        if (!isConsole) {
            // Piped or redirected stdin (e.g. an MSYS/git-bash pty is a named
            // pipe, not a console): a pipe read produces no terminal echo, so a
            // plain read is already echo-free. Only a real console needs the
            // char-by-char no-echo path below; failing here would make pairing
            // unusable in those shells for no echo-safety gain.
            return readlnOrNull()
        }

        val chars = CharArray(MAX_INPUT_LENGTH)
        var length = 0
        var result: String? = null
        var finished = false
        try {
            while (!finished) {
                when (val code = read_console_char_no_echo()) {
                    CARRIAGE_RETURN -> {
                        result = chars.concatToString(0, length)
                        finished = true
                    }
                    BACKSPACE -> if (length > 0) length--
                    EXTENDED_KEY_PREFIX,
                    EXTENDED_KEY_PREFIX_ALT,
                    -> read_console_char_no_echo() // Consume and ignore the extended key code.
                    END_OF_FILE,
                    WIDE_END_OF_FILE,
                    -> finished = true
                    else -> if (length < chars.size) chars[length++] = code.toChar()
                }
            }
        } finally {
            chars.fill('\u0000')
        }
        result
    }

private const val CARRIAGE_RETURN = 13
private const val BACKSPACE = 8
private const val END_OF_FILE = 26
private const val WIDE_END_OF_FILE = 0xFFFF
private const val EXTENDED_KEY_PREFIX = 0
private const val EXTENDED_KEY_PREFIX_ALT = 0xE0
private const val MAX_INPUT_LENGTH = 128
