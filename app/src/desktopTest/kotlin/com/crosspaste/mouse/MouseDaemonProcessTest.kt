package com.crosspaste.mouse

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Uses `runBlocking` (not `runTest`) because the reader job runs on real
// `Dispatchers.IO` threads against real `PipedInputStream`s — `runTest`'s
// virtual-time dispatcher would cause `withTimeout` to fire before any
// real I/O progress happens. See Task 3 Gotcha #1 in the plan.
class MouseDaemonProcessTest {

    @Test
    fun `reads one event per stdout line and parses it`() =
        runBlocking {
            val daemonStdoutSink = PipedOutputStream()
            val daemonStdoutSource = PipedInputStream(daemonStdoutSink)
            val daemonStdinSink = PipedOutputStream()
            // Host never reads stdin (the daemon does); we only need to see what was written.

            val process =
                MouseDaemonProcess(
                    stdout = daemonStdoutSource,
                    stdin = daemonStdinSink,
                    onClose = {},
                )

            // Write one line as if daemon emitted it
            daemonStdoutSink.write("""{"event":"ready","screens":[],"port":4243}""".toByteArray())
            daemonStdoutSink.write('\n'.code)
            daemonStdoutSink.flush()

            val ev = withTimeout(2000) { process.events.first() }
            assertTrue(ev is IpcEvent.Ready)
            assertEquals(4243, ev.port)

            process.close()
        }

    @Test
    fun `send writes a line-terminated JSON command to stdin`() =
        runBlocking {
            val daemonStdinSink = java.io.ByteArrayOutputStream()
            val process =
                MouseDaemonProcess(
                    stdout = java.io.ByteArrayInputStream(ByteArray(0)),
                    stdin = daemonStdinSink,
                    onClose = {},
                )

            process.send(IpcCommand.GetStatus)
            val written = daemonStdinSink.toString(Charsets.UTF_8)
            assertTrue(written.endsWith("\n"), "expected newline terminator, got: $written")
            assertTrue(written.contains(""""cmd":"get_status""""))

            process.close()
        }

    @Test
    fun `non-JSON stdout line is treated as log spillover and ignored`() =
        runBlocking {
            // The daemon's `plugin` mode occasionally lets log lines (with
            // ANSI color codes, "INFO ..." text, etc.) leak onto stdout
            // instead of stderr. Such lines should be logged on our side
            // but must NOT surface as IpcEvent.Error — that would show up
            // as a red banner in MouseSettings even though everything is
            // fine.
            val sink = PipedOutputStream()
            val source = PipedInputStream(sink)
            val process =
                MouseDaemonProcess(
                    stdout = source,
                    stdin = java.io.ByteArrayOutputStream(),
                    onClose = {},
                )
            sink.write("[2m2026-04-26T03:37:51Z[0m INFO whatever\n".toByteArray())
            sink.write("not json\n".toByteArray())
            sink.write("""{"event":"stopped"}""".toByteArray())
            sink.write('\n'.code)
            sink.flush()

            val collected =
                withTimeout(2000) {
                    val events = mutableListOf<IpcEvent>()
                    process.events
                        .takeWhile { ev ->
                            events.add(ev)
                            ev !is IpcEvent.Stopped
                        }.toList()
                    events
                }
            assertFalse(
                collected.any { it is IpcEvent.Error },
                "log spillover must not surface as Error: $collected",
            )
            assertTrue(collected.any { it is IpcEvent.Stopped })
            process.close()
        }

    @Test
    fun `malformed JSON object still emits Error event`() =
        runBlocking {
            // A line that starts with '{' is unambiguously meant to be a
            // protocol message — if we can't parse it that's a real bug
            // worth surfacing.
            val sink = PipedOutputStream()
            val source = PipedInputStream(sink)
            val process =
                MouseDaemonProcess(
                    stdout = source,
                    stdin = java.io.ByteArrayOutputStream(),
                    onClose = {},
                )
            sink.write("""{"event":"stopped"""".toByteArray()) // missing closing brace
            sink.write('\n'.code)
            sink.write("""{"event":"stopped"}""".toByteArray())
            sink.write('\n'.code)
            sink.flush()

            val collected =
                withTimeout(2000) {
                    val events = mutableListOf<IpcEvent>()
                    process.events
                        .takeWhile { ev ->
                            events.add(ev)
                            ev !is IpcEvent.Stopped
                        }.toList()
                    events
                }
            assertTrue(collected.any { it is IpcEvent.Error })
            assertTrue(collected.any { it is IpcEvent.Stopped })
            process.close()
        }
}
