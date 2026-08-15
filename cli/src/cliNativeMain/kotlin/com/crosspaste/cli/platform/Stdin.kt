package com.crosspaste.cli.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.fread
import platform.posix.stdin

/**
 * Reads standard input to EOF and decodes it as UTF-8. Used by `copy` for
 * piped input (`git log | crosspaste copy`); callers must ensure stdin is not
 * an interactive terminal first, or this blocks waiting for input.
 */
@OptIn(ExperimentalForeignApi::class)
fun readAllStdin(): String {
    val chunks = mutableListOf<ByteArray>()
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val read =
            buffer
                .usePinned { pinned ->
                    fread(pinned.addressOf(0), 1uL, buffer.size.toULong(), stdin)
                }.toInt()
        if (read <= 0) break
        chunks.add(buffer.copyOf(read))
    }
    val total = chunks.sumOf { it.size }
    val bytes = ByteArray(total)
    var offset = 0
    for (chunk in chunks) {
        chunk.copyInto(bytes, offset)
        offset += chunk.size
    }
    return bytes.decodeToString()
}
