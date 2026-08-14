package com.crosspaste.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import kotlinx.serialization.Serializable

@Serializable
data class CopyRequest(
    val text: String,
)

@Serializable
data class CopyResponse(
    val id: Long,
)

class CopyCommand : CliktCommand(name = "copy") {

    override fun help(context: Context): String = "Copy text to the clipboard via CrossPaste"

    private val text by argument(help = "Text to copy to clipboard")

    override fun run() =
        runCli { client ->
            val body = cliJson.encodeToString(CopyRequest.serializer(), CopyRequest(text))
            val response = client.postBody("/cli/copy", body, CopyResponse.serializer())
            echo("Copied to clipboard (id=${response.id}).")
        }
}
