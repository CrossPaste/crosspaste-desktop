package com.crosspaste.cli.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.errno
import platform.posix.ferror
import platform.posix.fread
import platform.posix.stdin
import platform.posix.strerror

/**
 * Everything read from stdin is buffered in memory, so cap it well above any
 * realistic text paste (the app rejects oversized text pastes anyway — its
 * default limit is 8 MB) instead of letting a runaway pipe OOM the CLI.
 */
const val MAX_STDIN_BYTES = 64 * 1024 * 1024

/** A problem with piped stdin input that must abort the copy (exit 1). */
sealed class StdinException(
    message: String,
) : Exception(message)

class StdinTooLargeException :
    StdinException("stdin input exceeds the ${MAX_STDIN_BYTES / (1024 * 1024)} MiB limit for text pastes")

class StdinReadException(
    message: String,
) : StdinException(message)

/**
 * Reads standard input to EOF and decodes it as UTF-8. Used by `copy` for
 * piped input (`git log | crosspaste copy`); callers must ensure stdin is not
 * an interactive terminal first, or this blocks waiting for input.
 *
 * @throws StdinTooLargeException when the input exceeds [MAX_STDIN_BYTES].
 * @throws StdinReadException on a read error (a short read is not EOF when
 *   ferror is set — treating it as one would silently copy truncated content)
 *   or when the input is not valid UTF-8 (strict decoding: silently replacing
 *   bad sequences with U+FFFD would corrupt the copied content).
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
    if (ferror(stdin) != 0) {
        val reason = strerror(errno)?.toKString() ?: "errno $errno"
        throw StdinReadException("failed to read stdin: $reason")
    }
    val bytes = ByteArray(total)
    var offset = 0
    for (chunk in chunks) {
        chunk.copyInto(bytes, offset)
        offset += chunk.size
    }
    return decodeStdinUtf8(bytes)
}

/** Split out from [readAllStdin] so the strict-decoding contract is testable. */
internal fun decodeStdinUtf8(bytes: ByteArray): String =
    try {
        bytes.decodeToString(throwOnInvalidSequence = true)
    } catch (_: CharacterCodingException) {
        throw StdinReadException("stdin is not valid UTF-8; only text can be copied")
    }
