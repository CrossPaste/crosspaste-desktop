package com.crosspaste.cli.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.errno
import platform.posix.fflush
import platform.posix.fwrite
import platform.posix.stdout
import platform.posix.strerror

class StdoutWriteException(
    message: String,
) : Exception(message)

/**
 * Writes raw bytes to stdout, bypassing any text-mode translation. Callers
 * must invoke [prepareStdoutForBinary] once first (on Windows the C runtime
 * would otherwise expand LF bytes to CRLF, corrupting binary payloads) and
 * [flushStdout] after the last chunk.
 */
@OptIn(ExperimentalForeignApi::class)
fun writeBytesToStdout(
    bytes: ByteArray,
    length: Int = bytes.size,
) {
    if (length == 0) return
    bytes.usePinned { pinned ->
        var written = 0
        while (written < length) {
            val n =
                fwrite(pinned.addressOf(written), 1uL, (length - written).toULong(), stdout).toInt()
            if (n <= 0) {
                val reason = strerror(errno)?.toKString() ?: "errno $errno"
                throw StdoutWriteException("failed to write to stdout: $reason")
            }
            written += n
        }
    }
}

/**
 * A failed flush means the tail of the payload never left the CRT buffer
 * (closed pipe, full disk) — that must surface as an error, not exit 0.
 */
@OptIn(ExperimentalForeignApi::class)
fun flushStdout() {
    if (fflush(stdout) != 0) {
        val reason = strerror(errno)?.toKString() ?: "errno $errno"
        throw StdoutWriteException("failed to flush stdout: $reason")
    }
}
