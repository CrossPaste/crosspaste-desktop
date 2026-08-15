package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.crosspaste.cli.api.AppNotRunningException
import com.crosspaste.cli.api.CLI_API_VERSION
import com.crosspaste.cli.api.CliClient
import com.crosspaste.cli.cliCommandName
import com.crosspaste.cli.platform.AppLiveness
import com.crosspaste.cli.platform.AppReadinessChecker
import com.crosspaste.cli.platform.CliAppPathProvider
import com.crosspaste.cli.platform.CliConfigReader
import com.crosspaste.cli.platform.createAppLauncher
import com.crosspaste.cli.platform.createNativePlatformPathProvider
import com.crosspaste.cli.platform.isGuiEnvironment
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.findObject
import com.github.ajalt.clikt.core.terminal
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/** Scripts can rely on this exit code to mean "CrossPaste is not running". */
const val EXIT_CODE_APP_NOT_RUNNING = 3

val cliJson =
    Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

/**
 * Runs [block] with a connected [CliClient]. Every command goes through this:
 * it runs the D5 liveness sequence first (waiting out a starting app, and
 * offering to launch a stopped one — see [ensureAppRunning]), surfaces
 * "app not running" uniformly, and warns (without failing) when the CLI and
 * app disagree on the API version.
 *
 * If the app goes away mid-command, the connection failure means the request
 * never reached it, so after re-running the liveness sequence the command is
 * retried once.
 */
fun CliktCommand.runCli(block: suspend (CliClient) -> Unit) {
    runBlocking {
        val configReader = CliConfigReader(createNativePlatformPathProvider())
        val readinessChecker = AppReadinessChecker(configReader)
        ensureAppRunning(readinessChecker)
        var retriedAfterRestart = false
        var versionWarningShown = false
        while (true) {
            var client: CliClient? = null
            try {
                client = CliClient(configReader)
                if (!versionWarningShown) {
                    versionWarningShown = warnOnApiVersionMismatch(client)
                }
                block(client)
                return@runBlocking
            } catch (e: ProgramResult) {
                throw e
            } catch (e: AppNotRunningException) {
                if (retriedAfterRestart) {
                    echo("Error: ${e.message}", err = true)
                    throw ProgramResult(EXIT_CODE_APP_NOT_RUNNING)
                }
                retriedAfterRestart = true
                ensureAppRunning(readinessChecker)
            } catch (e: Exception) {
                echo("Error: ${e.message}", err = true)
                throw ProgramResult(1)
            } finally {
                client?.close()
            }
        }
    }
}

/**
 * D5 unified pre-flight: running → proceed; starting (endpoint file present,
 * pid alive, socket not answering yet) → wait for readiness; not running →
 * offer to launch (see [startApp]). Throws [ProgramResult] when the command
 * cannot proceed.
 */
private suspend fun CliktCommand.ensureAppRunning(readinessChecker: AppReadinessChecker) {
    when (readinessChecker.probe()) {
        AppLiveness.RUNNING -> Unit
        AppLiveness.STARTING -> {
            echo("CrossPaste is starting; waiting for it to become ready...", err = true)
            if (!readinessChecker.waitForAppReady()) {
                echo(
                    "Error: Timed out waiting for CrossPaste to become ready. " +
                        "Please check the application manually.",
                    err = true,
                )
                throw ProgramResult(1)
            }
        }
        AppLiveness.NOT_RUNNING -> startApp(readinessChecker)
    }
}

/**
 * Launch consent follows the --start / --no-start tri-state: forced yes,
 * forced no, or (the default) a TTY prompt. A non-interactive caller is never
 * blocked on input: it fails fast with [EXIT_CODE_APP_NOT_RUNNING].
 */
private suspend fun CliktCommand.startApp(readinessChecker: AppReadinessChecker) {
    if (!isGuiEnvironment()) {
        echo(
            "Error: CrossPaste is not running, and this looks like a headless environment " +
                "(no DISPLAY or WAYLAND_DISPLAY), so the desktop application cannot be " +
                "started here. Headless daemon support is not available yet.",
            err = true,
        )
        throw ProgramResult(EXIT_CODE_APP_NOT_RUNNING)
    }
    if (!consentToStart()) {
        throw ProgramResult(EXIT_CODE_APP_NOT_RUNNING)
    }
    val starter = AppAutoStarter(createAppLauncher(CliAppPathProvider()), readinessChecker)
    val started = starter.startAndWait { message -> echo(message, err = true) }
    if (!started) {
        throw ProgramResult(1)
    }
}

private fun CliktCommand.consentToStart(): Boolean =
    when (currentContext.findObject<CliContext>()?.autoStart) {
        true -> true
        false -> {
            echo("Error: CrossPaste is not running.", err = true)
            false
        }
        null -> {
            if (terminal.terminalInfo.inputInteractive) {
                val confirmed = promptStartConfirmation()
                if (!confirmed) {
                    echo("Error: CrossPaste is not running.", err = true)
                }
                confirmed
            } else {
                echo(
                    "Error: CrossPaste is not running. " +
                        "Use '$cliCommandName --start <command>' to launch it automatically.",
                    err = true,
                )
                false
            }
        }
    }

/**
 * Hand-rolled instead of Mordant's YesNoPrompt so the prompt goes to stderr:
 * stdout must stay pipe-clean even when stdin is a TTY but stdout is
 * redirected (e.g. `crosspaste --json paste > out`).
 */
private fun CliktCommand.promptStartConfirmation(): Boolean {
    while (true) {
        echo("CrossPaste is not running. Start it now? [Y/n]: ", trailingNewline = false, err = true)
        val answer = readlnOrNull()?.trim()?.lowercase() ?: return false
        when (answer) {
            "", "y", "yes" -> return true
            "n", "no" -> return false
            else -> echo("Please answer y or n.", err = true)
        }
    }
}

/** Returns true when a warning was emitted, so callers can avoid repeats. */
internal fun CliktCommand.warnOnApiVersionMismatch(client: CliClient): Boolean {
    if (!client.hasApiVersionMismatch()) return false
    echo(
        "Warning: CLI API version $CLI_API_VERSION does not match the running " +
            "app's version ${client.endpoint.apiVersion}; consider updating both.",
        err = true,
    )
    return true
}

@OptIn(ExperimentalForeignApi::class)
fun formatRelativeTime(epochMillis: Long): String {
    val now = platform.posix.time(null) * 1000L
    val diff = now - epochMillis
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "${seconds}s ago"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 30 -> "${days}d ago"
        else -> {
            val months = days / 30
            "${months}mo ago"
        }
    }
}

fun formatSize(bytes: Long): String =
    when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
        else -> "${bytes / (1024 * 1024 * 1024)}GB"
    }
