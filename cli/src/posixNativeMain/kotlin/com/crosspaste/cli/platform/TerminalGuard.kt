package com.crosspaste.cli.platform

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import platform.posix.SIGHUP
import platform.posix.SIGINT
import platform.posix.SIGTERM
import platform.posix.SIG_DFL
import platform.posix.STDIN_FILENO
import platform.posix.STDOUT_FILENO
import platform.posix.TCSANOW
import platform.posix.raise
import platform.posix.signal
import platform.posix.tcgetattr
import platform.posix.tcsetattr
import platform.posix.termios
import platform.posix.write

// Everything the async signal handler touches is preallocated on the native
// heap at install time: allocating, encoding strings, or entering pinning
// scopes inside a signal handler is NOT async-signal-safe (the signal could
// have interrupted the allocator itself).
@OptIn(ExperimentalForeignApi::class)
private val savedTermios = nativeHeap.alloc<termios>()

private var termiosSaved = false

@OptIn(ExperimentalForeignApi::class)
private var restoreBuffer: CPointer<ByteVar>? = null

private var restoreLength = 0

/**
 * No-op on POSIX: Mordant's raw-mode exit restores the saved termios
 * correctly here. Only the native-Windows implementation needs the explicit
 * console-mode restore (see the mingw TerminalGuard).
 */
fun restoreConsoleModes() {}

/**
 * Restores the terminal when the process is killed from OUTSIDE while a
 * full-screen TUI owns it: in raw mode Ctrl-C arrives as a key event, but an
 * external SIGINT/SIGTERM/SIGHUP would otherwise leave the shell stuck in
 * raw mode on the alternate screen. The handler restores cooked mode, shows
 * the cursor, leaves the alternate screen, and re-raises the signal so the
 * exit status stays honest.
 */
@OptIn(ExperimentalForeignApi::class)
fun installTerminalGuard() {
    if (tcgetattr(STDIN_FILENO, savedTermios.ptr) == 0) {
        termiosSaved = true
    }
    if (restoreBuffer == null) {
        // Cursor show + alternate-screen exit, staged for a bare write(2)
        val esc = 27.toByte()
        val sequence =
            byteArrayOf(esc) + "[?25h".encodeToByteArray() +
                byteArrayOf(esc) + "[?1049l".encodeToByteArray()
        val buffer = nativeHeap.allocArray<ByteVar>(sequence.size)
        sequence.forEachIndexed { index, byte -> buffer[index] = byte }
        restoreBuffer = buffer
        restoreLength = sequence.size
    }
    val handler =
        staticCFunction { sig: Int ->
            // Async-signal-safe calls only: tcsetattr, write, signal, raise
            if (termiosSaved) {
                tcsetattr(STDIN_FILENO, TCSANOW, savedTermios.ptr)
            }
            restoreBuffer?.let { write(STDOUT_FILENO, it, restoreLength.convert()) }
            signal(sig, SIG_DFL)
            raise(sig)
            Unit
        }
    signal(SIGINT, handler)
    signal(SIGTERM, handler)
    signal(SIGHUP, handler)
}
