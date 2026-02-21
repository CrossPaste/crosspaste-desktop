package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import io.ktor.client.call.*
import kotlinx.serialization.Serializable

@Serializable
data class VersionInfo(
    val cliVersion: String,
    val appVersion: String?,
    val appRunning: Boolean,
)

class VersionCommand : CliktCommand(name = "version") {

    override fun help(context: Context): String = "Show CLI and app version"

    private val ctx by requireObject<CliContext>()

    override fun run() {
        val cliVersion = CLI_VERSION
        var appVersion: String? = null
        var appRunning = false

        try {
            runWithClient { client ->
                val response = client.get("/cli/status")
                handleResponse(response) { resp ->
                    val status = resp.body<StatusResponse>()
                    appVersion = status.appVersion
                    appRunning = true
                }
            }
        } catch (_: Exception) {
            // App not running - that's fine
        }

        if (ctx.json) {
            val info =
                VersionInfo(
                    cliVersion = cliVersion,
                    appVersion = appVersion,
                    appRunning = appRunning,
                )
            echo(cliJson.encodeToString(VersionInfo.serializer(), info))
        } else {
            echo("CLI version:  $cliVersion")
            if (appRunning) {
                echo("App version:  $appVersion")
            } else {
                echo("App version:  (not running)")
            }
        }
    }
}
