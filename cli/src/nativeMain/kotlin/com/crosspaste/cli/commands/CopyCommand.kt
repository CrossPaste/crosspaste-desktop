package com.crosspaste.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CopyRequest(
    val text: String,
)

class CopyCommand : CliktCommand(name = "copy") {

    override fun help(context: Context): String = "Copy text to the clipboard via CrossPaste"

    private val text by argument(help = "Text to copy to clipboard")

    override fun run() =
        runWithClient { client ->
            val body = Json.encodeToString(CopyRequest.serializer(), CopyRequest(text = text))
            val response = client.post("/cli/clipboard/write", body)
            handleResponse(response) {
                echo("Copied to clipboard.")
            }
        }
}
