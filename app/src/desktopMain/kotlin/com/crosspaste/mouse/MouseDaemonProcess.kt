package com.crosspaste.mouse

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.InterruptedIOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

private val logger = KotlinLogging.logger {}

/**
 * Wraps a running daemon subprocess for JSON Lines IPC.
 *
 * The constructor takes ownership of [stdout] and [stdin]: [close] will
 * close both. Tests injecting pipes should not reuse them after [close].
 */
class MouseDaemonProcess internal constructor(
    stdout: InputStream,
    stdin: OutputStream,
    private val onClose: () -> Unit,
) : AutoCloseable {

    private val stdoutStream: InputStream = stdout
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writer: BufferedWriter =
        BufferedWriter(OutputStreamWriter(stdin, StandardCharsets.UTF_8))
    private val writeLock = Mutex()

    private val _events =
        MutableSharedFlow<IpcEvent>(
            replay = 0,
            extraBufferCapacity = 256,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    /**
     * Events from the daemon (one per stdout line).
     *
     * Buffer is 256 events with [BufferOverflow.DROP_OLDEST]. Bursts beyond that
     * size — e.g. a rapid peer-churn storm — silently drop the oldest events.
     * Callers that need request/response correlation (e.g. [MouseDaemonClient])
     * must not assume every event is delivered; they should treat `getStatus` /
     * `getCapabilities` responses as best-effort and reissue on timeout.
     */
    val events: SharedFlow<IpcEvent> = _events.asSharedFlow()

    private val readerJob: Job =
        scope.launch {
            // `runInterruptible` translates coroutine cancellation into
            // `Thread.interrupt()`, which is the only way to unblock a JVM
            // classic-IO `readLine()` in progress — neither PipedInputStream.close()
            // nor coroutine cancel alone wake a reader parked in read().
            runInterruptible(Dispatchers.IO) {
                BufferedReader(InputStreamReader(stdout, StandardCharsets.UTF_8)).use { reader ->
                    // EOF is the only way out under normal flow; daemon restarts
                    // the session in-place via `stopped` events. close() interrupts
                    // readLine() via Thread.interrupt(); swallow the resulting
                    // InterruptedIOException so it doesn't leak onto the JVM's
                    // uncaught-exception queue (which confuses adjacent tests).
                    try {
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isBlank()) continue
                            // The daemon's `plugin` mode is supposed to emit only
                            // JSON Lines on stdout and route logs through stderr,
                            // but in practice some log output (with ANSI color
                            // codes — \x1b[…) leaks onto stdout. Treat anything
                            // that isn't a JSON object as log spillover: log
                            // it on the JVM side for diagnostics, but don't
                            // promote it to an IpcEvent.Error that would surface
                            // as a red banner in the settings UI.
                            val firstNonWhitespace = line.firstOrNull { !it.isWhitespace() }
                            if (firstNonWhitespace != '{') {
                                logger.warn { "non-JSON line on daemon stdout (log spillover): ${line.take(200)}" }
                                continue
                            }
                            val event =
                                runCatching {
                                    MouseIpcProtocol.json.decodeFromString(IpcEvent.serializer(), line)
                                }.getOrElse {
                                    IpcEvent.Error("malformed daemon output: ${it.message}")
                                }
                            _events.tryEmit(event)
                        }
                    } catch (_: InterruptedIOException) {
                        // expected on close()
                    }
                }
            }
        }

    /**
     * Send one command as a line on daemon stdin. Serialization + write +
     * flush is wrapped in [NonCancellable] on purpose: if the caller gets
     * cancelled mid-write, we'd otherwise leave a truncated JSON line in
     * the daemon's stdin — the Rust JSON-Lines parser would then fail the
     * next command (and every command thereafter) with a protocol error.
     *
     * Trade-off: a concurrent [close] call will wait on [writeLock] until
     * the in-flight send completes. That's intentional — the alternative
     * (yanking the writer mid-flush) would corrupt the daemon. The whole
     * write is short (single line, flushed immediately) so the wait is
     * bounded by OS pipe-buffer latency.
     */
    suspend fun send(command: IpcCommand) {
        val line = MouseIpcProtocol.json.encodeToString(IpcCommand.serializer(), command)
        writeLock.withLock {
            withContext(NonCancellable + Dispatchers.IO) {
                writer.write(line)
                writer.write("\n")
                writer.flush()
            }
        }
    }

    override fun close() {
        // Signal cancellation; runInterruptible translates this into
        // Thread.interrupt() on the reader thread, which unblocks readLine()
        // (InterruptedIOException). The reader coroutine then unwinds on its
        // own Dispatchers.IO thread.
        //
        // We intentionally do NOT runBlocking { cancelAndJoin() } here:
        // close() can be called from a coroutine context (e.g. the
        // MouseDaemonManager collector) and runBlocking inside a coroutine
        // is a deadlock risk if the caller's dispatcher is saturated or if
        // close() is ever invoked from inside the reader itself.
        // Trade-off: AutoCloseable returns before the reader is fully dead,
        // but the reader has no external side effects beyond _events.tryEmit
        // into a DROP_OLDEST buffer — safe.
        scope.cancel()
        runCatching { stdoutStream.close() }
        runCatching { writer.close() }
        onClose()
    }

    companion object {
        /**
         * Spawn the daemon binary in plugin mode. Caller must verify the binary
         * exists first (e.g. via [MouseDaemonBinary.resolve]).
         *
         * @throws IOException if the binary cannot be executed (not found, not
         * executable, etc.) — propagated from [ProcessBuilder.start].
         */
        fun spawn(binary: File): MouseDaemonProcess {
            val process =
                ProcessBuilder(binary.absolutePath, "plugin")
                    .redirectErrorStream(false) // keep stderr separate (daemon tracing logs go there)
                    .start()
            return MouseDaemonProcess(
                stdout = process.inputStream,
                stdin = process.outputStream,
                onClose = {
                    runCatching { process.destroy() }
                    runCatching { process.waitFor() }
                },
            )
        }
    }
}

fun MouseDaemonProcess.asDaemonHandle(): MouseDaemonClient.DaemonHandle =
    object : MouseDaemonClient.DaemonHandle {
        override val events = this@asDaemonHandle.events

        override suspend fun send(command: IpcCommand) = this@asDaemonHandle.send(command)

        override fun close() = this@asDaemonHandle.close()
    }
