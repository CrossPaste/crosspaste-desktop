package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.crosspaste.cli.platform.StdinException
import com.crosspaste.cli.platform.readAllStdin
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.findObject
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import kotlinx.serialization.Serializable

@Serializable
data class CopyRequest(
    val text: String,
)

@Serializable
data class CopyResponse(
    val id: Long,
)

class CopyCommand(
    /** Injectable so tests never touch the process's real stdin. */
    private val stdinReader: () -> String = ::readAllStdin,
) : CliktCommand(name = "copy") {

    override fun help(context: Context): String =
        "Copy text via CrossPaste: stores it in history and syncs it to your devices (reads stdin when piped)"

    // findObject instead of requireObject: tests run this command standalone
    private val ctx: CliContext? by findObject()

    private val text by argument(help = "Text to copy (omit to read piped stdin, e.g. `git log | crosspaste copy`)")
        .optional()

    override fun run() {
        val content = text ?: readPipedStdin()
        runCli { client ->
            val body = cliJson.encodeToString(CopyRequest.serializer(), CopyRequest(content))
            val response = client.postBody("/cli/copy", body, CopyResponse.serializer())
            if (ctx?.json == true) {
                echo(cliJson.encodeToString(CopyResponse.serializer(), response))
            } else {
                // "to CrossPaste", not "to clipboard": a headless daemon has no
                // system clipboard — the paste is stored in history and synced
                echo("Copied to CrossPaste (id=${response.id}).")
            }
        }
    }

    private fun readPipedStdin(): String {
        if (terminal.terminalInfo.inputInteractive) {
            throw usageError("provide text as an argument, or pipe it via stdin")
        }
        val content =
            try {
                stdinReader()
            } catch (e: StdinException) {
                echo("Error: ${e.message}", err = true)
                throw ProgramResult(1)
            }
        if (content.isEmpty()) {
            throw usageError("stdin was empty; nothing to copy")
        }
        return content
    }
}
