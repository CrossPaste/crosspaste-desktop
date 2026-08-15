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

enum class CliSymlinkState {
    /** Not macOS, not a production install, or the bundled CLI binary is absent (dev run). */
    NOT_SUPPORTED,

    /** The symlink exists and points at this bundle's CLI binary. */
    INSTALLED,

    /** No file at the link path. */
    NOT_INSTALLED,

    /** Something exists at the link path but it is not a link to this bundle's CLI. */
    NEEDS_REPAIR,
}

enum class CliInstallResult { SUCCESS, CANCELLED, FAILURE }

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

    private val _state = MutableStateFlow(computeState())
    val state: StateFlow<CliSymlinkState> = _state

    fun refresh() {
        _state.value = computeState()
    }

    private fun computeState(): CliSymlinkState {
        if (!supported || !Files.isRegularFile(cliBinaryPath.toNioPath())) {
            return CliSymlinkState.NOT_SUPPORTED
        }
        val nioLink = linkPath.toNioPath()
        if (!Files.exists(nioLink, LinkOption.NOFOLLOW_LINKS)) {
            return CliSymlinkState.NOT_INSTALLED
        }
        if (!Files.isSymbolicLink(nioLink)) {
            return CliSymlinkState.NEEDS_REPAIR
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
     * as a [CliInstallResult] and [state] is refreshed either way.
     */
    suspend fun install(): CliInstallResult =
        withContext(ioDispatcher) {
            if (computeState() == CliSymlinkState.NOT_SUPPORTED) {
                return@withContext CliInstallResult.FAILURE
            }
            val result =
                runCatching {
                    val nioLink = linkPath.toNioPath()
                    Files.deleteIfExists(nioLink)
                    Files.createSymbolicLink(nioLink, cliBinaryPath.toNioPath())
                    CliInstallResult.SUCCESS
                }.getOrElse { e ->
                    logger.info { "Direct symlink install failed (${e.message}); escalating to admin prompt" }
                    (escalatedInstall ?: ::osascriptInstall)(cliBinaryPath, linkPath)
                }
            refresh()
            result
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
            val script =
                "do shell script \"mkdir -p \" & quoted form of (item 2 of argv) & " +
                    "\" && ln -sf \" & quoted form of (item 1 of argv) & " +
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
                ).start()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            when {
                exitCode == 0 -> CliInstallResult.SUCCESS
                // AppleScript "User canceled." carries error number -128
                stderr.contains("-128") -> CliInstallResult.CANCELLED
                else -> {
                    logger.warn { "osascript symlink install failed (exit $exitCode): $stderr" }
                    CliInstallResult.FAILURE
                }
            }
        }.getOrElse { e ->
            logger.warn(e) { "Failed to run osascript for symlink install" }
            CliInstallResult.FAILURE
        }
}
