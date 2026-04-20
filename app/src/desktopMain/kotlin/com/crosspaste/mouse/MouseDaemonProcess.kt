package com.crosspaste.mouse

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
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

class MouseDaemonProcess internal constructor(
    stdout: InputStream,
    stdin: OutputStream,
    private val onClose: () -> Unit,
) : AutoCloseable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writer: BufferedWriter =
        BufferedWriter(OutputStreamWriter(stdin, StandardCharsets.UTF_8))
    private val writeLock = Mutex()

    private val _events =
        MutableSharedFlow<IpcEvent>(
            replay = 0,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val events: SharedFlow<IpcEvent> = _events.asSharedFlow()

    private val readerJob: Job =
        scope.launch {
            BufferedReader(InputStreamReader(stdout, StandardCharsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    val event =
                        runCatching {
                            MouseIpcProtocol.json.decodeFromString(IpcEvent.serializer(), line)
                        }.getOrElse {
                            IpcEvent.Error("malformed daemon output: ${it.message}")
                        }
                    _events.emit(event)
                    if (event is IpcEvent.Stopped) {
                        // keep reading — daemon may restart session; only exit on EOF.
                    }
                }
            }
        }

    suspend fun send(command: IpcCommand) {
        val line = MouseIpcProtocol.json.encodeToString(IpcCommand.serializer(), command)
        writeLock.withLock {
            withContext(Dispatchers.IO) {
                writer.write(line)
                writer.write("\n")
                writer.flush()
            }
        }
    }

    override fun close() {
        scope.cancel()
        runCatching { writer.close() }
        onClose()
    }

    companion object {
        /**
         * Spawns the daemon binary in plugin mode. Throws [IllegalStateException]
         * if the binary cannot be located.
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
