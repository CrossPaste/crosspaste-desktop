package com.crosspaste.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.serialization.Serializable

@Serializable
data class MessageResponse(
    val message: String,
)

class DeleteCommand : CliktCommand(name = "delete") {

    override fun help(context: Context): String = "Delete a paste by ID"

    private val id by argument(help = "Paste ID to delete").long()

    override fun run() =
        runCli { client ->
            val response = client.deleteBody("/cli/paste/$id", MessageResponse.serializer())
            echo(response.message)
        }
}
