package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import io.ktor.client.call.*
import kotlinx.serialization.Serializable

@Serializable
data class StatusResponse(
    val appVersion: String,
    val appInstanceId: String,
    val port: Int,
    val pasteboardListening: Boolean,
    val deviceCount: Int,
    val pasteCount: Long,
)

class StatusCommand : CliktCommand(name = "status") {

    override fun help(context: Context): String = "Show the status of the running CrossPaste application"

    private val ctx by requireObject<CliContext>()

    override fun run() =
        runWithClient { client ->
            val response = client.get("/cli/status")
            handleResponse(response) { resp ->
                val status = resp.body<StatusResponse>()
                if (ctx.json) {
                    echo(cliJson.encodeToString(StatusResponse.serializer(), status))
                } else {
                    printStatus(status)
                }
            }
        }

    private fun printStatus(status: StatusResponse) {
        val listening = if (status.pasteboardListening) "Active" else "Inactive"
        echo("CrossPaste v${status.appVersion}")
        echo("  Status:      Running")
        echo("  Port:        ${status.port}")
        echo("  Pasteboard:  $listening")
        echo("  Devices:     ${status.deviceCount}")
        echo("  Pastes:      ${status.pasteCount}")
        echo("  Instance:    ${status.appInstanceId}")
    }
}
