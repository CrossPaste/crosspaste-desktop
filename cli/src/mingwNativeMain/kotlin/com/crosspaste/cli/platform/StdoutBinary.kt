package com.crosspaste.cli.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.O_BINARY
import platform.posix._fileno
import platform.posix._setmode
import platform.posix.stdout

/**
 * Switches stdout to binary mode so the CRT stops expanding LF to CRLF —
 * mandatory before writing image bytes (`paste --raw` on an image paste).
 * Idempotent; affects only this process's stdout. A failure must abort:
 * writing on in text mode would silently corrupt the payload.
 */
@OptIn(ExperimentalForeignApi::class)
fun prepareStdoutForBinary() {
    if (_setmode(_fileno(stdout), O_BINARY) == -1) {
        throw StdoutWriteException("failed to switch stdout to binary mode")
    }
}
