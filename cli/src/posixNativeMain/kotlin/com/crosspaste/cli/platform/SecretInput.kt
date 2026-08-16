package com.crosspaste.cli.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getpass
import secretinput.clear_secret_buffer

/**
 * Reads a line through libc's terminal password reader. libc owns terminal-mode
 * restoration across interrupts; a failure is reported instead of falling back
 * to an echoed read.
 */
@OptIn(ExperimentalForeignApi::class)
fun readLineNoEcho(): String? {
    val buffer = getpass("") ?: error("Unable to read the pairing code without terminal echo.")
    return try {
        buffer.toKString()
    } finally {
        clear_secret_buffer(buffer)
    }
}
