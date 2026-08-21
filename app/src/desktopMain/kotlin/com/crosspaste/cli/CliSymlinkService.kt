package com.crosspaste.cli

import com.crosspaste.config.DevConfig
import com.crosspaste.path.AppPathProvider
import com.crosspaste.platform.Platform
import com.crosspaste.utils.getAppEnvUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import java.nio.file.Path as NioPath

enum class CliSymlinkState {
    /** Startup default until the first [CliSymlinkService.refresh] completes. */
    PROBING,

    /**
     * No CLI executable was found: a dev run without a built/configured CLI,
     * or a locally assembled package that omits the CLI payload.
     */
    BINARY_MISSING,

    /**
     * Windows/Linux: the terminal command is wired at install time (MSIX
     * AppExecutionAlias / deb symlink), which the app can only observe, not
     * manage. The CLI page shows the executable path and shell availability.
     */
    EXTERNALLY_MANAGED,

    /**
     * The app runs from a Gatekeeper App Translocation mount (randomized,
     * ephemeral path): a symlink installed now would die as soon as the app
     * exits or moves. The user must move CrossPaste to /Applications first;
     * installation is refused in this state.
     */
    TRANSLOCATED,

    /** The symlink exists and points at this bundle's CLI binary. */
    INSTALLED,

    /** No file at the link path. */
    NOT_INSTALLED,

    /** A symlink exists at the link path but points somewhere else (or dangles). */
    NEEDS_REPAIR,

    /**
     * A regular file or directory — not ours to delete — occupies the link
     * path. Automatic install/repair is refused; the user must resolve it
     * manually.
     */
    CONFLICT,
}

enum class CliInstallResult { SUCCESS, CANCELLED, FAILURE }

/** Whether the CLI command resolves in a given shell; [resolvedPath] is non-null when it does. */
data class ShellAvailability(
    val shell: String,
    val resolvedPath: String?,
) {
    val available: Boolean get() = resolvedPath != null
}

/**
 * Cross-platform surface for the bundled CLI (design decision D6, revised
 * 2026-08-21: the settings entry is visible on every platform and in dev
 * runs; only the install action stays macOS-specific).
 *
 * - macOS: maintains the /usr/local/bin/crosspaste symlink pointing at the
 *   bundled CLI. Installing first tries a plain symlink (works when
 *   /usr/local/bin is user-writable, e.g. Homebrew-on-Intel machines) and
 *   falls back to an osascript "with administrator privileges" prompt (the
 *   VS Code model) — /usr/local/bin is root-owned or absent on stock installs.
 * - Windows/Linux: PATH wiring happens at install time (MSIX execution alias
 *   `crosspaste-cli` / deb symlink /usr/bin/crosspaste), so the state is
 *   [CliSymlinkState.EXTERNALLY_MANAGED] and only observation (executable
 *   path, shell availability) is offered.
 * - Dev runs: the binary is resolved from `cliBinaryPath` in
 *   development.properties when set (a configured-but-missing path fails
 *   fast), otherwise from the conventional `cli/dist/<target>/` output of
 *   `./gradlew :cli:assembleDist`; nothing found means
 *   [CliSymlinkState.BINARY_MISSING], never a crash.
 */
class CliSymlinkService(
    appPathProvider: AppPathProvider,
    private val platform: Platform,
    private val linkPath: Path = DEFAULT_LINK_PATH.toPath(),
    private val escalatedInstall: ((cliBinary: Path, link: Path) -> CliInstallResult)? = null,
    binaryResolverOverride: (() -> Path?)? = null,
) {

    companion object {
        const val DEFAULT_LINK_PATH = "/usr/local/bin/crosspaste"

        const val COMMAND_NAME = "crosspaste"

        /** `crosspaste.exe` is Conveyor's GUI-launch alias, so the CLI keeps its own name. */
        const val WINDOWS_COMMAND_NAME = "crosspaste-cli"

        private val PROBED_UNIX_SHELLS = listOf("/bin/zsh", "/bin/bash")

        /** The marker is only printed when `command -v` succeeded, so parsing needs no exit-code check. */
        private val PROBE_COMMAND =
            "p=\$(command -v $COMMAND_NAME) && printf '\\n$RESOLVED_MARKER%s\\n' \"\$p\""

        private val PROBE_TIMEOUT = 5.seconds

        private const val MAX_PROBE_LINE_BYTES = 64 * 1024
    }

    private val logger = KotlinLogging.logger {}

    /** What the user actually types in a terminal on this platform. */
    val commandName: String =
        if (platform.isWindows()) WINDOWS_COMMAND_NAME else COMMAND_NAME

    /**
     * AppImage mounts the payload at a new temporary path on every launch,
     * so "add the executable's folder to PATH" guidance would be wrong there
     * (documented D6 limitation); the runtime exports APPIMAGE when set.
     */
    val runsFromAppImage: Boolean =
        platform.isLinux() && !System.getenv("APPIMAGE").isNullOrEmpty()

    /**
     * Yields the current CLI binary location, re-evaluated on every refresh.
     * Packaged builds bundle the CLI in the JVM payload area, which is
     * exactly what pasteAppJarPath points at (Contents/Resources on macOS,
     * lib/app on Linux, app\ on Windows) — a fixed address. Dev runs resolve
     * it from development.properties when configured (validated once,
     * failing fast on a stale path), otherwise from the cli/dist convention
     * — re-walked each time so building the CLI while the app runs is picked
     * up by the next refresh, without restarting.
     */
    private val binaryResolver: () -> Path? =
        binaryResolverOverride ?: createBinaryResolver(appPathProvider)

    private fun createBinaryResolver(appPathProvider: AppPathProvider): () -> Path? {
        if (!getAppEnvUtils().isDevelopment()) {
            val packaged =
                appPathProvider.pasteAppJarPath
                    .resolve("bin")
                    .resolve(cliBinaryFileName(platform))
            return { packaged }
        }
        val configured =
            DevConfig.cliBinaryPath?.let {
                resolveDevCliBinary(
                    configuredPath = it,
                    startDir = Paths.get("").toAbsolutePath(),
                    distTarget = devCliDistTarget(platform),
                    binaryFileName = cliBinaryFileName(platform),
                )
            }
        if (configured != null) {
            return { configured }
        }
        val distTarget = devCliDistTarget(platform)
        val binaryFileName = cliBinaryFileName(platform)
        return {
            resolveDevCliBinary(
                configuredPath = null,
                startDir = Paths.get("").toAbsolutePath(),
                distTarget = distTarget,
                binaryFileName = binaryFileName,
            )
        }
    }

    // Both flows start empty/pessimistic and are populated by refresh():
    // resolution and computeState() do filesystem probes, which must not run
    // on the main thread where Koin first constructs this service.
    private val _cliBinaryPath = MutableStateFlow<Path?>(null)
    val cliBinaryPath: StateFlow<Path?> = _cliBinaryPath

    private val _state = MutableStateFlow(CliSymlinkState.PROBING)
    val state: StateFlow<CliSymlinkState> = _state

    suspend fun refresh() {
        withContext(ioDispatcher) {
            val binary = binaryResolver()
            _cliBinaryPath.value = binary
            _state.value = computeState(binary)
        }
    }

    private fun computeState(binary: Path?): CliSymlinkState {
        if (binary == null || !Files.isRegularFile(binary.toNioPath())) {
            return CliSymlinkState.BINARY_MISSING
        }
        if (!platform.isMacos()) {
            return CliSymlinkState.EXTERNALLY_MANAGED
        }
        if (binary.toString().contains("/AppTranslocation/")) {
            return CliSymlinkState.TRANSLOCATED
        }
        val nioLink = linkPath.toNioPath()
        if (!Files.exists(nioLink, LinkOption.NOFOLLOW_LINKS)) {
            return CliSymlinkState.NOT_INSTALLED
        }
        if (!Files.isSymbolicLink(nioLink)) {
            return CliSymlinkState.CONFLICT
        }
        val target = runCatching { Files.readSymbolicLink(nioLink) }.getOrNull()
        return if (target?.normalize() == binary.toNioPath().normalize()) {
            CliSymlinkState.INSTALLED
        } else {
            CliSymlinkState.NEEDS_REPAIR
        }
    }

    /**
     * Creates or repairs the symlink. Never throws; the outcome is reported
     * as a [CliInstallResult] and [state] is refreshed either way. Only the
     * NOT_INSTALLED and NEEDS_REPAIR states are actionable — and because that
     * pre-check races against other processes, both the direct attempt and
     * the escalated shell command re-verify at execution time that nothing
     * but a symlink is ever deleted. SUCCESS is only reported when the link
     * verifiably points at the bundled CLI afterwards.
     */
    suspend fun install(): CliInstallResult =
        withContext(ioDispatcher) {
            val cliBinary = binaryResolver()
            _cliBinaryPath.value = cliBinary
            val current = computeState(cliBinary)
            if (current != CliSymlinkState.NOT_INSTALLED && current != CliSymlinkState.NEEDS_REPAIR) {
                _state.value = current
                return@withContext CliInstallResult.FAILURE
            }
            // Actionable states imply the binary resolved
            if (cliBinary == null) {
                return@withContext CliInstallResult.FAILURE
            }
            val result =
                attemptDirectInstall(cliBinary)
                    ?: (escalatedInstall ?: ::osascriptInstall)(cliBinary, linkPath)
            val finalState = computeState(cliBinary)
            _state.value = finalState
            when {
                result != CliInstallResult.SUCCESS -> result
                // A "successful" escalation may still not have produced the
                // right link (e.g. an unexpected filesystem state); only a
                // verified link counts as success
                finalState == CliSymlinkState.INSTALLED -> CliInstallResult.SUCCESS
                else -> CliInstallResult.FAILURE
            }
        }

    /**
     * Execution-time-guarded direct attempt: deletes only a symlink, fails on
     * a foreign file that appeared after the pre-check (without escalating —
     * asking for admin credentials would not make destroying it acceptable),
     * and returns null when escalation should take over (e.g. no write
     * permission).
     */
    internal fun attemptDirectInstall(cliBinary: Path): CliInstallResult? {
        val nioLink = linkPath.toNioPath()
        if (Files.exists(nioLink, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(nioLink)) {
            logger.warn { "A foreign file appeared at $linkPath; refusing to replace it" }
            return CliInstallResult.FAILURE
        }
        return runCatching {
            if (Files.isSymbolicLink(nioLink)) {
                Files.deleteIfExists(nioLink)
            }
            Files.createSymbolicLink(nioLink, cliBinary.toNioPath())
            CliInstallResult.SUCCESS
        }.getOrElse { e ->
            logger.info { "Direct symlink install failed (${e.message}); escalating to admin prompt" }
            null
        }
    }

    /**
     * Answers "can the user actually type [commandName] in a terminal right
     * now?" On Unix this asks each login shell to resolve the command — which
     * honors whatever PATH the user's shell profiles set up, not just our
     * default symlink location. On Windows it asks `where.exe` (the cmd view
     * of PATH, which includes the WindowsApps alias directory) and Windows
     * PowerShell's `Get-Command`.
     */
    suspend fun probeShellAvailability(): List<ShellAvailability> =
        withContext(ioDispatcher) {
            if (platform.isWindows()) {
                probeWindowsAvailability()
            } else {
                probedUnixShells().map { shell ->
                    ShellAvailability(
                        shell = shell.substringAfterLast('/'),
                        resolvedPath = resolveCommandIn(shell),
                    )
                }
            }
        }

    /** zsh/bash where present (Linux often lacks zsh); /bin/sh as a last resort. */
    private fun probedUnixShells(): List<String> =
        PROBED_UNIX_SHELLS
            .filter { Files.isRegularFile(Paths.get(it)) }
            .ifEmpty { listOf("/bin/sh") }

    private suspend fun probeWindowsAvailability(): List<ShellAvailability> =
        listOf(
            ShellAvailability(
                shell = "cmd",
                resolvedPath =
                    resolveViaProcess(
                        listOf("where.exe", commandName),
                        ::parseWherePath,
                    ),
            ),
            ShellAvailability(
                shell = "powershell",
                resolvedPath =
                    resolveViaProcess(
                        listOf(
                            "powershell.exe",
                            "-NoProfile",
                            "-NonInteractive",
                            "-Command",
                            // Windows PowerShell 5.1-safe: plain cmdlet + string
                            // concatenation, no ScriptBlock/thread tricks
                            "\$p=(Get-Command $commandName -ErrorAction SilentlyContinue).Source; " +
                                "if(\$p){Write-Output ('$RESOLVED_MARKER' + \$p)}",
                        ),
                        ::parseResolvedCommand,
                    ),
            ),
        )

    /**
     * -l: login shell, so the user's profile PATH applies — which also means
     * the profile may print banners, spawn background jobs that inherit
     * stdout (keeping the pipe open after the shell exits, so EOF may never
     * come), or emit arbitrary amounts of text. See [resolveViaProcess] for
     * how the probe stays robust against all of that.
     */
    internal suspend fun resolveCommandIn(
        shell: String,
        probeCommand: String = PROBE_COMMAND,
        timeout: Duration = PROBE_TIMEOUT,
    ): String? = resolveViaProcess(listOf(shell, "-lc", probeCommand), ::parseResolvedCommand, timeout)

    /**
     * Runs [command] and scans its stdout line by line with [parseLine] until
     * a line yields a result. The probe never blocks on the stream: it polls
     * available bytes with a bounded buffer, returns the moment a line
     * parses, and gives up as soon as the process has exited with nothing
     * left to scan. Timeout and cancellation ride on cancellable [delay]s;
     * cleanup (closing our pipe end, killing a still-running process) happens
     * in finally either way. A background child of the process is not ours to
     * kill — we just stop reading from it.
     */
    internal suspend fun resolveViaProcess(
        command: List<String>,
        parseLine: (String) -> String?,
        timeout: Duration = PROBE_TIMEOUT,
    ): String? {
        val process =
            try {
                ProcessBuilder(command)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
            } catch (e: IOException) {
                logger.warn(e) { "Failed to run ${command.firstOrNull()} to probe for $commandName" }
                return null
            }
        try {
            return withTimeoutOrNull(timeout) { awaitParsedLine(process, parseLine) }
        } finally {
            runCatching { process.inputStream.close() }
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
    }

    private suspend fun awaitParsedLine(
        process: Process,
        parseLine: (String) -> String?,
    ): String? {
        val stream = process.inputStream
        val lineBuffer = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (true) {
            var progressed = false
            while (stream.available() > 0) {
                val n = stream.read(chunk, 0, minOf(chunk.size, stream.available()))
                if (n <= 0) break
                progressed = true
                for (i in 0 until n) {
                    val byte = chunk[i]
                    if (byte == '\n'.code.toByte()) {
                        val line = lineBuffer.toString(Charsets.UTF_8.name())
                        lineBuffer.reset()
                        parseLine(line)?.let { return it }
                    } else if (lineBuffer.size() < MAX_PROBE_LINE_BYTES) {
                        // Longer lines cannot be a resolved path (paths are
                        // orders of magnitude shorter); cap so a firehose
                        // profile cannot balloon memory
                        lineBuffer.write(byte.toInt())
                    }
                }
                // A continuously chatty profile can keep available() positive
                // forever. Yield once per bounded chunk so timeout and caller
                // cancellation are always observed.
                yield()
            }
            if (!process.isAlive && stream.available() == 0) {
                // Everything the process wrote has been scanned; scan the
                // final unterminated line, then never wait for EOF — a
                // background child may hold the pipe open forever
                return parseLine(lineBuffer.toString(Charsets.UTF_8.name()))
            }
            if (!progressed) {
                delay(20.milliseconds)
            }
        }
    }

    /**
     * `do shell script … with administrator privileges` shows the standard
     * macOS credentials dialog. Paths travel as argv and are quoted with
     * AppleScript's `quoted form of`, so spaces and quotes in the install
     * location cannot break the shell command.
     */
    private fun osascriptInstall(
        cliBinary: Path,
        link: Path,
    ): CliInstallResult =
        runCatching {
            val shellCommand =
                INSTALL_SHELL_TEMPLATE
                    .replace("%CLI%", "\" & quoted form of (item 1 of argv) & \"")
                    .replace("%DIR%", "\" & quoted form of (item 2 of argv) & \"")
                    .replace("%LINK%", "\" & quoted form of (item 3 of argv) & \"")
            val script = "do shell script \"$shellCommand\" with administrator privileges"
            val process =
                ProcessBuilder(
                    "osascript",
                    "-e",
                    "on run argv",
                    "-e",
                    script,
                    "-e",
                    "end run",
                    cliBinary.toString(),
                    (link.parent ?: link).toString(),
                    link.toString(),
                    // The command produces no stdout today, but discard it
                    // anyway so a full pipe can never deadlock the single
                    // stderr read below.
                ).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            val result = classifyOsascriptResult(exitCode, stderr)
            if (result == CliInstallResult.FAILURE) {
                logger.warn { "osascript symlink install failed (exit $exitCode): $stderr" }
            }
            result
        }.getOrElse { e ->
            logger.warn(e) { "Failed to run osascript for symlink install" }
            CliInstallResult.FAILURE
        }
}

/** Tags the shell-availability probe's answer so profile banners can never be mistaken for it. */
internal const val RESOLVED_MARKER = "__CROSSPASTE_RESOLVED__"

/** Extracts the marker-tagged path from a probe's output, ignoring any profile noise around it. */
internal fun parseResolvedCommand(output: String): String? =
    output
        .lineSequence()
        .firstOrNull { it.startsWith(RESOLVED_MARKER) }
        ?.removePrefix(RESOLVED_MARKER)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

/** `where.exe` prints one bare path per match; the first non-empty line wins. */
internal fun parseWherePath(line: String): String? = line.trim().takeIf { it.isNotEmpty() }

/** The packaged (and dev-built) CLI file name for [platform]. */
internal fun cliBinaryFileName(platform: Platform): String =
    if (platform.isWindows()) "crosspaste-cli.exe" else "crosspaste-cli"

/** Maps the host platform to `:cli:assembleDist`'s output directory under cli/dist/. */
internal fun devCliDistTarget(platform: Platform): String {
    val arm = platform.arch.contains("aarch64") || platform.arch.contains("arm")
    return when {
        platform.isWindows() -> "mingwX64"
        platform.isMacos() -> if (arm) "macosArm64" else "macosX64"
        else -> if (arm) "linuxArm64" else "linuxX64"
    }
}

/**
 * Dev-run binary resolution: an explicitly configured path wins but must
 * exist — a stale development.properties entry fails fast instead of
 * silently degrading. Without configuration, walks up from [startDir] (the
 * run's working directory, whose depth relative to the repo root varies by
 * launcher) looking for the conventional `cli/dist/<target>/` build output;
 * null when the CLI simply hasn't been built, which must not block app
 * development that never touches it.
 */
internal fun resolveDevCliBinary(
    configuredPath: String?,
    startDir: NioPath,
    distTarget: String,
    binaryFileName: String,
): Path? {
    if (configuredPath != null) {
        // Absolute + normalized before validating and returning: a relative
        // configured path would otherwise be written verbatim into the
        // /usr/local/bin symlink, resolve relative to /usr/local/bin as a
        // dangling link — and still compare equal as INSTALLED
        val configured = Paths.get(configuredPath).toAbsolutePath().normalize()
        check(Files.isRegularFile(configured)) {
            "development.properties cliBinaryPath does not point to a file: $configuredPath"
        }
        return configured.toOkioPath()
    }
    var dir: NioPath? = startDir.toAbsolutePath().normalize()
    while (dir != null) {
        val candidate =
            dir
                .resolve("cli")
                .resolve("dist")
                .resolve(distTarget)
                .resolve(binaryFileName)
        if (Files.isRegularFile(candidate)) {
            return candidate.toOkioPath()
        }
        dir = dir.parent
    }
    return null
}

/**
 * The escalated install command, executed by /bin/sh as root. It re-verifies
 * the link path at execution time: only a symlink is ever removed, and any
 * other occupant makes it bail out with exit 40 before touching anything —
 * the state check done before requesting credentials can race against other
 * processes. Plain `ln -s` (not -sf): `-sf` through a surviving
 * link-to-directory would create the new link INSIDE that directory and
 * still exit 0. BSD ln's -h/-n would prevent that, but removing the link
 * first and creating it plainly needs no non-POSIX flags at all.
 *
 * %CLI% / %DIR% / %LINK% are replaced with AppleScript `quoted form of` argv
 * references at runtime (and with shell-quoted literal paths in tests).
 */
internal const val INSTALL_SHELL_TEMPLATE =
    "mkdir -p %DIR%; " +
        "if [ -L %LINK% ]; then rm -f %LINK%; fi; " +
        "if [ -e %LINK% ] || [ -L %LINK% ]; then exit 40; fi; " +
        "ln -s %CLI% %LINK%"

/** Maps an osascript run to a result; AppleScript reports a declined credentials dialog as error (-128). */
internal fun classifyOsascriptResult(
    exitCode: Int,
    stderr: String,
): CliInstallResult =
    when {
        exitCode == 0 -> CliInstallResult.SUCCESS
        stderr.contains("(-128)") -> CliInstallResult.CANCELLED
        else -> CliInstallResult.FAILURE
    }
