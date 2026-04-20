package com.crosspaste.mouse

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

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
                    // the session in-place via `stopped` events.
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) continue
                        val event =
                            runCatching {
                                MouseIpcProtocol.json.decodeFromString(IpcEvent.serializer(), line)
                            }.getOrElse {
                                IpcEvent.Error("malformed daemon output: ${it.message}")
                            }
                        _events.tryEmit(event)
                    }
                }
            }
        }

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
        // Close the stdout stream first to unblock any reader thread parked in
        // `readLine()` (coroutine cancellation alone can't interrupt a blocking
        // JVM I/O call). Then cancel the scope and wait for the reader to fully
        // unwind, so the AutoCloseable contract is honored: no worker thread
        // outlives close().
        runCatching { stdoutStream.close() }
        runCatching {
            runBlocking {
                scope.coroutineContext[Job]?.cancelAndJoin()
            }
        }
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
