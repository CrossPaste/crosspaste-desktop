package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int

class SearchCommand : CliktCommand(name = "search") {

    override fun help(context: Context): String = "Search paste history"

    private val ctx by requireObject<CliContext>()

    private val query by argument(help = "Search query")

    private val limit by option("--limit", "-n", help = "Number of results").int().default(20)

    private val type by option("--type", "-t", help = "Filter by type (text, link, image, file, html, rtf, color)")

    private val tag by option("--tag", "-g", help = "Filter by tag name")

    private val format by listFormatOption()

    override fun run() =
        runCli { client ->
            val list =
                client.getBody(
                    "/cli/search${buildListQuery(limit, type, tag, query = query)}",
                    PasteListResponse.serializer(),
                )

            when (resolveListFormat(format, ctx.json)) {
                ListFormat.JSON -> echo(cliJson.encodeToString(PasteListResponse.serializer(), list))
                ListFormat.ID -> list.items.forEach { echo(it.id) }
                ListFormat.TABLE -> printResults(list)
            }
        }

    private fun printResults(list: PasteListResponse) {
        if (list.items.isEmpty()) {
            echo("No results found for \"$query\".")
            return
        }
        echo("${list.items.size} result(s):")
        echo("")
        printPasteRows(list.items, showRemote = false, showSize = false)
    }
}
