package com.crosspaste.cli

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
import okio.Path.Companion.toPath
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

enum class CliSymlinkState {
    /** Not macOS, not a production install, or the bundled CLI binary is absent (dev run). */
    NOT_SUPPORTED,

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

/** Whether `crosspaste` resolves in a given shell; [resolvedPath] is non-null when it does. */
data class ShellAvailability(
    val shell: String,
    val resolvedPath: String?,
) {
    val available: Boolean get() = resolvedPath != null
}

/**
 * macOS PATH integration for the bundled CLI (design decision D6): maintains
 * the /usr/local/bin/crosspaste symlink pointing at
 * CrossPaste.app/Contents/Resources/bin/crosspaste-cli.
 *
 * Installing first tries a plain symlink (works when /usr/local/bin is
 * user-writable, e.g. Homebrew-on-Intel machines) and falls back to an
 * osascript "with administrator privileges" prompt (the VS Code model) —
 * /usr/local/bin is root-owned or absent on stock installs.
 *
 * On deb-based Linux and MSIX Windows the equivalent wiring happens at
 * install time (packaging symlink / app execution alias), so this service
 * reports [CliSymlinkState.NOT_SUPPORTED] everywhere but macOS.
 */
class CliSymlinkService(
    appPathProvider: AppPathProvider,
    platform: Platform,
    private val linkPath: Path = DEFAULT_LINK_PATH.toPath(),
    supportedOverride: Boolean? = null,
    private val escalatedInstall: ((cliBinary: Path, link: Path) -> CliInstallResult)? = null,
) {

    companion object {
        const val DEFAULT_LINK_PATH = "/usr/local/bin/crosspaste"

        const val COMMAND_NAME = "crosspaste"

        private val PROBED_SHELLS = listOf("/bin/zsh", "/bin/bash")

        /** The marker is only printed when `command -v` succeeded, so parsing needs no exit-code check. */
        private val PROBE_COMMAND =
            "p=\$(command -v $COMMAND_NAME) && printf '\\n$RESOLVED_MARKER%s\\n' \"\$p\""

        private val PROBE_TIMEOUT = 5.seconds

        private const val MAX_PROBE_LINE_BYTES = 64 * 1024
    }

    private val logger = KotlinLogging.logger {}

    /**
     * The bundled CLI lives in the JVM payload area, which is exactly what
     * pasteAppJarPath points at (Contents/Resources on macOS).
     */
    val cliBinaryPath: Path =
        appPathProvider.pasteAppJarPath
            .resolve("bin")
            .resolve("crosspaste-cli")

    private val supported: Boolean =
        supportedOverride ?: (platform.isMacos() && getAppEnvUtils().isProduction())

    // Starts pessimistic and is populated by refresh(): computeState() does
    // filesystem probes, which must not run on the main thread where Koin
    // first constructs this service.
    private val _state = MutableStateFlow(CliSymlinkState.NOT_SUPPORTED)
    val state: StateFlow<CliSymlinkState> = _state

    suspend fun refresh() {
        withContext(ioDispatcher) {
            _state.value = computeState()
        }
    }

    private fun computeState(): CliSymlinkState {
        if (!supported || !Files.isRegularFile(cliBinaryPath.toNioPath())) {
            return CliSymlinkState.NOT_SUPPORTED
        }
        if (cliBinaryPath.toString().contains("/AppTranslocation/")) {
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
        return if (target?.normalize() == cliBinaryPath.toNioPath().normalize()) {
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
            val current = computeState()
            if (current != CliSymlinkState.NOT_INSTALLED && current != CliSymlinkState.NEEDS_REPAIR) {
                _state.value = current
                return@withContext CliInstallResult.FAILURE
            }
            val result =
                attemptDirectInstall()
                    ?: (escalatedInstall ?: ::osascriptInstall)(cliBinaryPath, linkPath)
            val finalState = computeState()
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
    internal fun attemptDirectInstall(): CliInstallResult? {
        val nioLink = linkPath.toNioPath()
        if (Files.exists(nioLink, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(nioLink)) {
            logger.warn { "A foreign file appeared at $linkPath; refusing to replace it" }
            return CliInstallResult.FAILURE
        }
        return runCatching {
            if (Files.isSymbolicLink(nioLink)) {
                Files.deleteIfExists(nioLink)
            }
            Files.createSymbolicLink(nioLink, cliBinaryPath.toNioPath())
            CliInstallResult.SUCCESS
        }.getOrElse { e ->
            logger.info { "Direct symlink install failed (${e.message}); escalating to admin prompt" }
            null
        }
    }

    /**
     * Answers "can the user actually type `crosspaste` in a terminal right
     * now?" by asking each login shell to resolve the command — this honors
     * whatever PATH the user's shell profiles set up, not just our default
     * symlink location.
     */
    suspend fun probeShellAvailability(): List<ShellAvailability> =
        withContext(ioDispatcher) {
            PROBED_SHELLS.map { shell ->
                ShellAvailability(
                    shell = shell.substringAfterLast('/'),
                    resolvedPath = resolveCommandIn(shell),
                )
            }
        }

    /**
     * -l: login shell, so the user's profile PATH applies — which also means
     * the profile may print banners, spawn background jobs that inherit
     * stdout (keeping the pipe open after the shell exits, so EOF may never
     * come), or emit arbitrary amounts of text. The probe therefore never
     * blocks on the stream: it polls available bytes, scans line by line
     * with a bounded buffer, returns the moment the marker line appears, and
     * gives up as soon as the shell has exited with nothing left to scan.
     * Timeout and cancellation ride on cancellable [delay]s; cleanup (closing
     * our pipe end, killing a still-running shell) happens in finally either
     * way. A background child of the shell is not ours to kill — we just stop
     * reading from it.
     */
    internal suspend fun resolveCommandIn(
        shell: String,
        probeCommand: String = PROBE_COMMAND,
        timeout: Duration = PROBE_TIMEOUT,
    ): String? {
        val process =
            try {
                ProcessBuilder(shell, "-lc", probeCommand)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
            } catch (e: IOException) {
                logger.warn(e) { "Failed to probe $shell for $COMMAND_NAME" }
                return null
            }
        try {
            return withTimeoutOrNull(timeout) { awaitMarker(process) }
        } finally {
            runCatching { process.inputStream.close() }
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
    }

    private suspend fun awaitMarker(process: Process): String? {
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
                        parseResolvedCommand(line)?.let { return it }
                    } else if (lineBuffer.size() < MAX_PROBE_LINE_BYTES) {
                        // Longer lines cannot be the marker line (paths are
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
                // Everything the shell wrote has been scanned and no marker
                // appeared; never wait for EOF — a background child may hold
                // the pipe open forever
                return null
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

/** Extracts the marker-tagged path from a login shell's output, ignoring any profile noise around it. */
internal fun parseResolvedCommand(output: String): String? =
    output
        .lineSequence()
        .firstOrNull { it.startsWith(RESOLVED_MARKER) }
        ?.removePrefix(RESOLVED_MARKER)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

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
