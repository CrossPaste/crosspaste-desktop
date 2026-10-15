package com.crosspaste.plugin

import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.Path
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** [exitCode] is null when the run hit the timeout and the process tree was killed. */
class PluginProcessResult(
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
)

/**
 * Runs one plugin command per call: argv without a shell, the plugin
 * directory as working directory, [input] as UTF-8 on stdin, stdout decoded
 * as UTF-8. On timeout or cancellation the whole process tree is killed.
 */
class ProcessPluginExecutor(
    private val cliBinaryPath: () -> String?,
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
    ): PluginProcessResult {
        val workingDir = directory.toFile()
        val process =
            withContext(ioDispatcher) {
                ProcessBuilder(resolvePluginCommand(workingDir, command))
                    .directory(workingDir)
                    .also { configurePluginEnvironment(it.environment(), cliBinaryPath(), extraPathDirs) }
                    .start()
            }
        // Blocking pipe I/O lives outside the caller's scope: a reader stuck on a
        // pipe that an orphaned grandchild still holds must not hang the caller
        val pipes = CoroutineScope(ioDispatcher)
        val stdout = pipes.async { process.inputStream.use { it.readNBytes(MAX_STDOUT_BYTES) } }
        val stderr = pipes.async { process.errorStream.use { it.readNBytes(MAX_STDERR_BYTES) } }
        pipes.launch {
            // A plugin may exit without reading its input; the broken pipe is expected
            runCatching { process.outputStream.use { it.write(input.encodeToByteArray()) } }
        }
        try {
            return withTimeoutOrNull(timeout) {
                val exitCode = process.onExit().await().exitValue()
                val outBytes = withTimeoutOrNull(pipeDrainTimeout) { stdout.await() } ?: ByteArray(0)
                val errBytes = withTimeoutOrNull(pipeDrainTimeout) { stderr.await() } ?: ByteArray(0)
                PluginProcessResult(
                    exitCode = exitCode,
                    stdout = decodePluginOutput(outBytes),
                    stderr = decodePluginOutput(errBytes),
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
 * for itself, and adds UTF-8 for Python plus the bundled CLI location. The
 * [extraPathDirs] the requirement check searched are appended to PATH, so a
 * tool reported as available is also found when the plugin runs.
 */
internal fun configurePluginEnvironment(
    env: MutableMap<String, String>,
    cliBinaryPath: String?,
    extraPathDirs: List<String> = emptyList(),
) {
    env.keys.removeAll { it.startsWith("JAVA_MAIN_CLASS_") || it.startsWith("JAVA_STARTED_ON_FIRST_THREAD_") }
    if (extraPathDirs.isNotEmpty()) {
        val pathKey = env.keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "PATH"
        val pathDirs = env[pathKey].orEmpty().split(File.pathSeparatorChar).filter { it.isNotBlank() }
        env[pathKey] = (pathDirs + extraPathDirs.filterNot { it in pathDirs }).joinToString(File.pathSeparator)
    }
    env["PYTHONIOENCODING"] = "utf-8"
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
