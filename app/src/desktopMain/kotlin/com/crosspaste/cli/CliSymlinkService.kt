package com.crosspaste.cli

import com.crosspaste.path.AppPathProvider
import com.crosspaste.platform.Platform
import com.crosspaste.utils.getAppEnvUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okio.Path
import okio.Path.Companion.toPath
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.concurrent.TimeUnit

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
     * NOT_INSTALLED and NEEDS_REPAIR states are actionable: TRANSLOCATED and
     * CONFLICT refuse so a foreign file is never deleted and a doomed link is
     * never created. SUCCESS is only reported when the link verifiably points
     * at the bundled CLI afterwards.
     */
    suspend fun install(): CliInstallResult =
        withContext(ioDispatcher) {
            val current = computeState()
            if (current != CliSymlinkState.NOT_INSTALLED && current != CliSymlinkState.NEEDS_REPAIR) {
                _state.value = current
                return@withContext CliInstallResult.FAILURE
            }
            val result =
                runCatching {
                    val nioLink = linkPath.toNioPath()
                    // The guard above only allows a missing path or a symlink
                    // here, so this delete can never destroy a foreign file
                    Files.deleteIfExists(nioLink)
                    Files.createSymbolicLink(nioLink, cliBinaryPath.toNioPath())
                    CliInstallResult.SUCCESS
                }.getOrElse { e ->
                    logger.info { "Direct symlink install failed (${e.message}); escalating to admin prompt" }
                    (escalatedInstall ?: ::osascriptInstall)(cliBinaryPath, linkPath)
                }
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

    private fun resolveCommandIn(shell: String): String? =
        runCatching {
            // -l: login shell, so the user's profile PATH applies. Output is
            // at most one short path, far below pipe capacity, so waiting
            // before reading cannot deadlock — and lets a hung shell profile
            // be killed instead of blocking a read forever.
            val process =
                ProcessBuilder(shell, "-lc", "command -v $COMMAND_NAME")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return@runCatching null
            }
            val output =
                process.inputStream
                    .bufferedReader()
                    .readText()
                    .trim()
            if (process.exitValue() == 0 && output.isNotEmpty()) {
                output.lineSequence().first()
            } else {
                null
            }
        }.getOrElse { e ->
            logger.warn(e) { "Failed to probe $shell for $COMMAND_NAME" }
            null
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
            // rm -f + plain ln -s instead of ln -sf: macOS ln has no -h/-n,
            // so ln -sf through an existing link-to-directory would create
            // the new link INSIDE that directory and still exit 0. rm -f
            // removes a symlink without following it and refuses directories.
            val script =
                "do shell script \"mkdir -p \" & quoted form of (item 2 of argv) & " +
                    "\" && rm -f \" & quoted form of (item 3 of argv) & " +
                    "\" && ln -s \" & quoted form of (item 1 of argv) & " +
                    "\" \" & quoted form of (item 3 of argv) " +
                    "with administrator privileges"
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
