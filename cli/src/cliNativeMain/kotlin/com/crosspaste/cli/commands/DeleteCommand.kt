package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.serialization.Serializable

@Serializable
data class MessageResponse(
    val message: String,
)

class DeleteCommand : CliktCommand(name = "delete") {

    override fun help(context: Context): String = "Delete a paste by ID"

    private val ctx by requireObject<CliContext>()

    private val id by argument(help = "Paste ID to delete").long()

    override fun run() =
        runCli { client ->
            val response = client.deleteBody("/cli/paste/$id", MessageResponse.serializer())
            echoMessage(ctx, response)
        }
}

/** Shared tail for message-only commands: JSON passthrough with --json, plain text otherwise. */
internal fun CliktCommand.echoMessage(
    ctx: CliContext,
    response: MessageResponse,
) {
    if (ctx.json) {
        echo(cliJson.encodeToString(MessageResponse.serializer(), response))
    } else {
        echo(response.message)
    }
}
