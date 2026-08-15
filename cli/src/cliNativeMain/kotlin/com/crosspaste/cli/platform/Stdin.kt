package com.crosspaste.cli.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.fread
import platform.posix.stdin

/**
 * Everything read from stdin is buffered in memory, so cap it well above any
 * realistic text paste (the app rejects oversized text pastes anyway — its
 * default limit is 8 MB) instead of letting a runaway pipe OOM the CLI.
 */
const val MAX_STDIN_BYTES = 64 * 1024 * 1024

class StdinTooLargeException :
    Exception("stdin input exceeds the ${MAX_STDIN_BYTES / (1024 * 1024)} MiB limit for text pastes")

/**
 * Reads standard input to EOF and decodes it as UTF-8. Used by `copy` for
 * piped input (`git log | crosspaste copy`); callers must ensure stdin is not
 * an interactive terminal first, or this blocks waiting for input.
 *
 * @throws StdinTooLargeException when the input exceeds [MAX_STDIN_BYTES].
 */
@OptIn(ExperimentalForeignApi::class)
fun readAllStdin(): String {
    val chunks = mutableListOf<ByteArray>()
    val buffer = ByteArray(64 * 1024)
    var total = 0
    while (true) {
        val read =
            buffer
                .usePinned { pinned ->
                    fread(pinned.addressOf(0), 1uL, buffer.size.toULong(), stdin)
                }.toInt()
        if (read <= 0) break
        total += read
        if (total > MAX_STDIN_BYTES) {
            throw StdinTooLargeException()
        }
        chunks.add(buffer.copyOf(read))
    }
    val bytes = ByteArray(total)
    var offset = 0
    for (chunk in chunks) {
        chunk.copyInto(bytes, offset)
        offset += chunk.size
    }
    return bytes.decodeToString()
}
