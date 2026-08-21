package com.crosspaste.cli.platform

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import platform.posix.ECHO
import platform.posix.ICANON
import platform.posix.POLLIN
import platform.posix.STDIN_FILENO
import platform.posix.STDOUT_FILENO
import platform.posix.TCSANOW
import platform.posix.VMIN
import platform.posix.VTIME
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.read
import platform.posix.tcgetattr
import platform.posix.tcsetattr
import platform.posix.termios
import platform.posix.write
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private const val READ_CHUNK_BYTES = 256

/**
 * Runs one raw-mode query transaction against the controlling terminal:
 * silences echo and line buffering, writes [payload] to stdout, accumulates
 * reply bytes until [isComplete] says the transaction is done or
 * [timeoutMillis] elapses, then restores the previous terminal state.
 *
 * Returns whatever was read — possibly incomplete or empty on timeout;
 * parsers treat missing parts as absent — or null when the terminal could not
 * be switched to raw mode at all. Callers must only invoke this when stdin
 * and stdout are both interactive, and must NOT hold another raw-mode session
 * (Mordant's picker raw mode) open at the same time: `pick` probes once
 * before entering its TUI and caches the result. Deliberately not Mordant's
 * RawModeScope — that parses input into key events and would consume or
 * mangle the terminal's DA reply. (Per-platform like SecretInput, not
 * expect/actual: cliNativeMain resolves it in each target compilation.)
 */
@OptIn(ExperimentalForeignApi::class)
internal fun queryTerminalRaw(
    payload: String,
    timeoutMillis: Int,
    isComplete: (String) -> Boolean,
): String? =
    memScoped {
        val saved = alloc<termios>()
        if (tcgetattr(STDIN_FILENO, saved.ptr) != 0) return null
        val raw = alloc<termios>()
        if (tcgetattr(STDIN_FILENO, raw.ptr) != 0) return null
        // No echo (the reply must not land on screen), no line buffering (it
        // arrives without a newline); reads are non-blocking, poll() waits.
        val cookedFlags = (ECHO or ICANON).inv()
        raw.c_lflag = raw.c_lflag and cookedFlags.convert()
        raw.c_cc[VMIN] = 0u
        raw.c_cc[VTIME] = 0u
        if (tcsetattr(STDIN_FILENO, TCSANOW, raw.ptr) != 0) return null
        try {
            val bytes = payload.encodeToByteArray()
            val writeResult =
                bytes.usePinned { pinned ->
                    write(STDOUT_FILENO, pinned.addressOf(0), bytes.size.convert())
                }
            if (writeResult != bytes.size.toLong()) return ""
            val reply = StringBuilder()
            val deadline = TimeSource.Monotonic.markNow() + timeoutMillis.milliseconds
            val buffer = allocArray<ByteVar>(READ_CHUNK_BYTES)
            val fds = alloc<pollfd>()
            fds.fd = STDIN_FILENO
            fds.events = POLLIN.convert()
            while (!isComplete(reply.toString())) {
                val remaining = (deadline - TimeSource.Monotonic.markNow()).inWholeMilliseconds
                if (remaining <= 0) break
                fds.revents = 0
                if (poll(fds.ptr, 1.convert(), remaining.toInt()) <= 0) break
                val count = read(STDIN_FILENO, buffer, READ_CHUNK_BYTES.convert())
                if (count <= 0) break
                reply.append(buffer.readBytes(count.toInt()).decodeToString())
            }
            reply.toString()
        } finally {
            tcsetattr(STDIN_FILENO, TCSANOW, saved.ptr)
        }
    }
