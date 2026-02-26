package com.crosspaste.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.long

class FavCommand : CliktCommand(name = "fav") {

    override fun help(context: Context): String = "Toggle favorite status of a paste"

    private val id by argument(help = "Paste ID to toggle favorite").long()

    override fun run() =
        runWithClient { client ->
            val response = client.put("/cli/paste/$id/favorite")
            handleResponse(response) {
                echo("Paste #$id favorite toggled.")
            }
        }
}
