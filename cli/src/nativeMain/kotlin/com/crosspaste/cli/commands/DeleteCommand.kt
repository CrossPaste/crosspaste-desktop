package com.crosspaste.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.long

class DeleteCommand : CliktCommand(name = "delete") {

    override fun help(context: Context): String = "Delete a paste by ID"

    private val id by argument(help = "Paste ID to delete").long()

    override fun run() =
        runWithClient { client ->
            val response = client.delete("/cli/paste/$id")
            handleResponse(response) {
                echo("Paste #$id deleted.")
            }
        }
}
