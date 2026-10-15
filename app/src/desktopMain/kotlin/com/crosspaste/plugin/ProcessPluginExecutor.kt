package com.crosspaste.plugin

import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.await
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.Path
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [exitCode] is null when the run hit the timeout and the process tree was
 * killed. [stdoutTruncated] means stdout went past the size limit, so [stdout]
 * holds only its beginning.
 */
class PluginProcessResult(
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
    val stdoutTruncated: Boolean = false,
)

/**
 * Runs one plugin command per call: argv without a shell, the plugin
 * directory as working directory, [input] as UTF-8 on stdin, stdout decoded
 * as UTF-8. On timeout or cancellation the whole process tree is killed.
 */
class ProcessPluginExecutor(
    private val cliBinaryPath: () -> String?,
    private val language: () -> String,
    private val extraPathDirs: List<String> = emptyList(),
    private val timeout: Duration = DEFAULT_TIMEOUT,
    private val pipeDrainTimeout: Duration = DEFAULT_PIPE_DRAIN_TIMEOUT,
) {

    companion object {
        val DEFAULT_TIMEOUT = 30.seconds
        val DEFAULT_PIPE_DRAIN_TIMEOUT = 2.seconds
        const val MAX_STDOUT_BYTES = 10 * 1024 * 1024
        const val MAX_STDERR_BYTES = 64 * 1024
    }

    suspend fun execute(
        directory: Path,
        command: List<String>,
        input: String,
        action: String? = null,
    ): PluginProcessResult {
        val workingDir = directory.toFile()
        val process =
            withContext(ioDispatcher) {
                ProcessBuilder(resolvePluginCommand(workingDir, command))
                    .directory(workingDir)
                    .also {
                        configurePluginEnvironment(
                            it.environment(),
                            cliBinaryPath(),
                            language(),
                            action,
                            extraPathDirs,
                        )
                    }.start()
            }
        // Blocking pipe I/O lives outside the caller's scope: a reader stuck on a
        // pipe that an orphaned grandchild still holds must not hang the caller
        val pipes = CoroutineScope(ioDispatcher)
        val stdout = PipeCapture(MAX_STDOUT_BYTES)
        val stderr = PipeCapture(MAX_STDERR_BYTES)
        val drains =
            listOf(
                pipes.launch { runCatching { stdout.drain(process.inputStream) } },
                pipes.launch { runCatching { stderr.drain(process.errorStream) } },
            )
        pipes.launch {
            // A plugin may exit without reading its input; the broken pipe is expected
            runCatching { process.outputStream.use { it.write(input.encodeToByteArray()) } }
        }
        try {
            return withTimeoutOrNull(timeout) {
                val exitCode = process.onExit().await().exitValue()
                // A grandchild that inherited the pipes can keep them open after the
                // plugin exits; keep whatever arrived instead of waiting for EOF
                withTimeoutOrNull(pipeDrainTimeout) { drains.joinAll() }
                PluginProcessResult(
                    exitCode = exitCode,
                    stdout = decodePluginOutput(stdout.bytes()),
                    stderr = decodePluginOutput(stderr.bytes()),
                    stdoutTruncated = stdout.truncated,
                )
            } ?: PluginProcessResult(exitCode = null, stdout = "", stderr = "")
        } finally {
            if (process.isAlive) {
                killProcessTree(process)
            }
            pipes.cancel()
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            runCatching { process.outputStream.close() }
        }
    }
}

/**
 * Reads a pipe to EOF, keeping at most [limit] bytes. Reading continues past the
 * limit so a chatty plugin never blocks on a full pipe, and what was kept stays
 * readable while the drain is still running.
 */
internal class PipeCapture(
    private val limit: Int,
) {
    private val buffer = ByteArrayOutputStream()

    @Volatile
    var truncated = false
        private set

    fun drain(stream: InputStream) {
        stream.use {
            val chunk = ByteArray(8192)
            while (true) {
                val read = it.read(chunk)
                if (read < 0) break
                synchronized(buffer) {
                    val kept = minOf(read, limit - buffer.size())
                    if (kept > 0) buffer.write(chunk, 0, kept)
                    if (kept < read) truncated = true
                }
            }
        }
    }

    fun bytes(): ByteArray = synchronized(buffer) { buffer.toByteArray() }
}

/**
 * A relative executable containing a path separator (`./tool`, `bin\tool.exe`)
 * resolves against the plugin directory; Windows would otherwise resolve it
 * against the app's working directory. Bare names (`powershell`) keep the PATH
 * lookup, and arguments resolve naturally through the working directory.
 */
internal fun resolvePluginCommand(
    directory: File,
    command: List<String>,
): List<String> {
    if (command.isEmpty()) return emptyList()
    val executable = command.first()
    val isRelativePath =
        !File(executable).isAbsolute && (executable.contains('/') || executable.contains('\\'))
    val resolved = if (isRelativePath) File(directory, executable).path else executable
    return listOf(resolved) + command.drop(1)
}

/**
 * Inherits the user's environment minus the variables the JVM launcher sets
 * for itself, and adds UTF-8 for Python, the bundled CLI location, the app's
 * UI [language] (a copywriter code such as `en` or `zh_hant`) and the chosen
 * [action] id (only for plugins that declare actions). The
 * [extraPathDirs] the requirement check searched are appended to PATH, so a
 * tool reported as available is also found when the plugin runs.
 */
internal fun configurePluginEnvironment(
    env: MutableMap<String, String>,
    cliBinaryPath: String?,
    language: String,
    action: String? = null,
    extraPathDirs: List<String> = emptyList(),
) {
    env.keys.removeAll { it.startsWith("JAVA_MAIN_CLASS_") || it.startsWith("JAVA_STARTED_ON_FIRST_THREAD_") }
    if (extraPathDirs.isNotEmpty()) {
        val pathKey = env.keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "PATH"
        val pathDirs = env[pathKey].orEmpty().split(File.pathSeparatorChar).filter { it.isNotBlank() }
        env[pathKey] = (pathDirs + extraPathDirs.filterNot { it in pathDirs }).joinToString(File.pathSeparator)
    }
    env["PYTHONIOENCODING"] = "utf-8"
    env["CROSSPASTE_LANGUAGE"] = language
    if (action != null) {
        env["CROSSPASTE_ACTION"] = action
    } else {
        env.remove("CROSSPASTE_ACTION")
    }
    if (cliBinaryPath != null) {
        env["CROSSPASTE_CLI"] = cliBinaryPath
    } else {
        env.remove("CROSSPASTE_CLI")
    }
}

/** UTF-8 without the BOM Windows PowerShell likes to emit, with CRLF normalized to LF. */
internal fun decodePluginOutput(bytes: ByteArray): String =
    bytes
        .decodeToString()
        .removePrefix("\uFEFF")
        .replace("\r\n", "\n")

/** Descendants are collected first: once the parent dies they are reparented and unreachable. */
private fun killProcessTree(process: Process) {
    val descendants = process.descendants().toList()
    process.destroyForcibly()
    descendants.forEach { it.destroyForcibly() }
}
