package com.crosspaste.cli.platform

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.windows.DWORDVar
import platform.windows.GetConsoleMode
import platform.windows.GetNumberOfConsoleInputEvents
import platform.windows.GetStdHandle
import platform.windows.HANDLE
import platform.windows.INPUT_RECORD
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.KEY_EVENT
import platform.windows.ReadConsoleInputW
import platform.windows.STD_INPUT_HANDLE
import platform.windows.STD_OUTPUT_HANDLE
import platform.windows.SetConsoleMode
import platform.windows.WaitForSingleObject
import platform.windows.WriteFile
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

// Console-mode flags spelled out locally so compilation does not depend on
// how recent the bundled mingw-w64 headers are.
private const val VT_INPUT_MODE: UInt = 0x0200u // ENABLE_VIRTUAL_TERMINAL_INPUT
private const val VT_PROCESSED_OUTPUT: UInt = 0x0001u // ENABLE_PROCESSED_OUTPUT
private const val VT_OUTPUT_PROCESSING: UInt = 0x0004u // ENABLE_VIRTUAL_TERMINAL_PROCESSING

private const val INPUT_RECORD_BATCH = 32

/**
 * Windows variant of the raw-mode query transaction (contract documented on
 * the POSIX sibling): VT input mode delivers the terminal's reply as an ESC
 * character stream in key-down records, VT output processing makes conpty
 * forward the query sequences to the hosting terminal.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun queryTerminalRaw(
    payload: String,
    timeoutMillis: Int,
    isComplete: (String) -> Boolean,
): String? =
    memScoped {
        val stdinHandle = GetStdHandle(STD_INPUT_HANDLE)
        val stdoutHandle = GetStdHandle(STD_OUTPUT_HANDLE)
        if (stdinHandle == null || stdinHandle == INVALID_HANDLE_VALUE) return null
        if (stdoutHandle == null || stdoutHandle == INVALID_HANDLE_VALUE) return null
        val savedInputMode = alloc<DWORDVar>()
        val savedOutputMode = alloc<DWORDVar>()
        if (GetConsoleMode(stdinHandle, savedInputMode.ptr) == 0) return null
        if (GetConsoleMode(stdoutHandle, savedOutputMode.ptr) == 0) return null
        if (SetConsoleMode(stdinHandle, VT_INPUT_MODE) == 0) return null
        if (SetConsoleMode(
                stdoutHandle,
                savedOutputMode.value or VT_PROCESSED_OUTPUT or VT_OUTPUT_PROCESSING,
            ) == 0
        ) {
            SetConsoleMode(stdinHandle, savedInputMode.value)
            return null
        }
        try {
            if (writeConsolePayload(stdoutHandle, payload)) {
                readConsoleReply(stdinHandle, timeoutMillis, isComplete)
            } else {
                ""
            }
        } finally {
            SetConsoleMode(stdinHandle, savedInputMode.value)
            SetConsoleMode(stdoutHandle, savedOutputMode.value)
        }
    }

@OptIn(ExperimentalForeignApi::class)
private fun writeConsolePayload(
    stdoutHandle: HANDLE,
    payload: String,
): Boolean =
    memScoped {
        val bytes = payload.encodeToByteArray()
        val written = alloc<DWORDVar>()
        val writeOk =
            bytes.usePinned { pinned ->
                WriteFile(
                    stdoutHandle,
                    pinned.addressOf(0),
                    bytes.size.convert(),
                    written.ptr,
                    null,
                )
            }
        writeOk != 0 && written.value.toInt() == bytes.size
    }

@OptIn(ExperimentalForeignApi::class)
private fun readConsoleReply(
    stdinHandle: HANDLE,
    timeoutMillis: Int,
    isComplete: (String) -> Boolean,
): String =
    memScoped {
        val reply = StringBuilder()
        val deadline = TimeSource.Monotonic.markNow() + timeoutMillis.milliseconds
        val records = allocArray<INPUT_RECORD>(INPUT_RECORD_BATCH)
        val eventCount = alloc<DWORDVar>()
        val readCount = alloc<DWORDVar>()
        while (!isComplete(reply.toString())) {
            val remaining = (deadline - TimeSource.Monotonic.markNow()).inWholeMilliseconds
            if (remaining <= 0) break
            // 0u == WAIT_OBJECT_0 (input available); timeout and failure
            // codes are both non-zero (spelled out to dodge the header
            // constant's platform-dependent Kotlin type)
            if (WaitForSingleObject(stdinHandle, remaining.toUInt()) != 0u) break
            if (GetNumberOfConsoleInputEvents(stdinHandle, eventCount.ptr) == 0) break
            if (eventCount.value == 0u) continue
            // ReadConsoleInput consumes the queued records, so non-key events
            // (focus, resize) cannot leave the handle signaled forever; the
            // VT reply itself arrives as key-down records carrying characters
            if (ReadConsoleInputW(
                    stdinHandle,
                    records,
                    INPUT_RECORD_BATCH.convert(),
                    readCount.ptr,
                ) == 0
            ) {
                break
            }
            appendKeyChars(records, readCount.value.toInt(), reply)
        }
        reply.toString()
    }

@OptIn(ExperimentalForeignApi::class)
private fun appendKeyChars(
    records: CPointer<INPUT_RECORD>,
    count: Int,
    reply: StringBuilder,
) {
    for (i in 0 until count) {
        val record = records[i]
        if (record.EventType.toInt() != KEY_EVENT) continue
        val keyEvent = record.Event.KeyEvent
        if (keyEvent.bKeyDown == 0) continue
        val char = keyEvent.uChar.UnicodeChar.toInt()
        if (char == 0) continue
        // One record can carry a run of identical characters ("22" may
        // arrive as a single record with wRepeatCount=2 — dropping the
        // repeats would corrupt the DA1/cell-size reply); a zero count is
        // defensively treated as one so a character is never lost
        repeat(maxOf(keyEvent.wRepeatCount.toInt(), 1)) {
            reply.append(char.toChar())
        }
    }
}
