package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.crosspaste.cli.api.AppNotRunningException
import com.crosspaste.cli.api.CliClient
import com.crosspaste.cli.platform.AppLiveness
import com.crosspaste.cli.platform.AppReadinessChecker
import com.crosspaste.cli.platform.CliConfigReader
import com.crosspaste.cli.platform.createNativePlatformPathProvider
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.mordant.rendering.TextColors
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class StatusResponse(
    val appVersion: String,
    val appInstanceId: String,
    val apiVersion: Int = 0,
    val port: Int,
    val pasteboardListening: Boolean,
    val deviceCount: Int,
    val pasteCount: Long,
)

@Serializable
data class StatusOutput(
    val running: Boolean,
    val state: String,
    val appVersion: String? = null,
    val appInstanceId: String? = null,
    val apiVersion: Int? = null,
    val port: Int? = null,
    val pasteboardListening: Boolean? = null,
    val deviceCount: Int? = null,
    val pasteCount: Long? = null,
)

/**
 * Unlike every other command, status never triggers auto-start: it reports
 * the app's actual state (Running / Starting / Not running) and exits with
 * [EXIT_CODE_APP_NOT_RUNNING] unless the app is ready to serve.
 */
class StatusCommand : CliktCommand(name = "status") {

    override fun help(context: Context): String =
        "Show the status of the CrossPaste application (never starts it; " +
            "exit code $EXIT_CODE_APP_NOT_RUNNING when it is not running)"

    private val ctx by requireObject<CliContext>()

    private val statusJson =
        Json(cliJson) {
            explicitNulls = false
        }

    override fun run() {
        runBlocking {
            val configReader = CliConfigReader(createNativePlatformPathProvider())
            val readinessChecker = AppReadinessChecker(configReader)
            when (readinessChecker.probe()) {
                AppLiveness.RUNNING -> printRunning(configReader, readinessChecker)
                AppLiveness.STARTING -> printNotReady("Starting", "starting")
                AppLiveness.NOT_RUNNING -> printNotReady("Not running", "not_running")
            }
        }
    }

    private suspend fun printRunning(
        configReader: CliConfigReader,
        readinessChecker: AppReadinessChecker,
    ) {
        var client: CliClient? = null
        try {
            client = CliClient(configReader)
            warnOnApiVersionMismatch(client)
            val status = client.getBody("/cli/status", StatusResponse.serializer())
            printStatus(status)
        } catch (_: AppNotRunningException) {
            // The app went away between the probe and the status request
            printNotReady("Not running", "not_running")
        } catch (e: Exception) {
            // An EOF/reset from an app that died mid-request is still "not
            // running"; only keep exit 1 when the app demonstrably serves
            when (readinessChecker.probe()) {
                AppLiveness.RUNNING -> {
                    echo("Error: ${e.message}", err = true)
                    throw ProgramResult(1)
                }
                AppLiveness.STARTING -> printNotReady("Starting", "starting")
                AppLiveness.NOT_RUNNING -> printNotReady("Not running", "not_running")
            }
        } finally {
            client?.close()
        }
    }

    private fun printStatus(status: StatusResponse) {
        if (ctx.json) {
            val output =
                StatusOutput(
                    running = true,
                    state = "running",
                    appVersion = status.appVersion,
                    appInstanceId = status.appInstanceId,
                    apiVersion = status.apiVersion,
                    port = status.port,
                    pasteboardListening = status.pasteboardListening,
                    deviceCount = status.deviceCount,
                    pasteCount = status.pasteCount,
                )
            echo(statusJson.encodeToString(StatusOutput.serializer(), output))
            return
        }
        val listening = if (status.pasteboardListening) "Active" else "Inactive"
        echo("CrossPaste v${status.appVersion}")
        echo("  Status:      ${TextColors.green("Running")}")
        echo("  Port:        ${status.port}")
        echo("  Pasteboard:  $listening")
        echo("  Devices:     ${status.deviceCount}")
        echo("  Pastes:      ${status.pasteCount}")
        echo("  Instance:    ${status.appInstanceId}")
    }

    private fun printNotReady(
        label: String,
        state: String,
    ): Nothing {
        if (ctx.json) {
            val output = StatusOutput(running = false, state = state)
            echo(statusJson.encodeToString(StatusOutput.serializer(), output))
        } else {
            val styled = if (state == "starting") TextColors.yellow(label) else TextColors.red(label)
            echo("CrossPaste")
            echo("  Status:      $styled")
        }
        throw ProgramResult(EXIT_CODE_APP_NOT_RUNNING)
    }
}
