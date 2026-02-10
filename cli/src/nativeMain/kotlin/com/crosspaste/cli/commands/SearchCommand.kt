package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import io.ktor.client.call.*

class SearchCommand : CliktCommand(name = "search") {

    override fun help(context: Context): String = "Search paste history"

    private val ctx by requireObject<CliContext>()

    private val query by argument(help = "Search query")

    private val limit by option("--limit", "-n", help = "Number of results").int().default(20)

    private val type by option("--type", "-t", help = "Filter by type (text, link, image, file, html, rtf, color)")

    override fun run() =
        runWithClient { client ->
            var path = "/cli/paste/search?q=${encodeQuery(query)}&limit=$limit"
            type?.let { path += "&type=$it" }

            val response = client.get(path)
            handleResponse(response) { resp ->
                val list = resp.body<PasteListResponse>()
                if (ctx.json) {
                    echo(cliJson.encodeToString(PasteListResponse.serializer(), list))
                } else {
                    printResults(list)
                }
            }
        }

    private fun printResults(list: PasteListResponse) {
        if (list.items.isEmpty()) {
            echo("No results found for \"$query\".")
            return
        }
        echo("${list.items.size} result(s):")
        echo("")
        for (item in list.items) {
            val fav = if (item.favorite) "*" else " "
            val preview = item.preview.replace("\n", " ").take(60)
            echo(
                "$fav ${item.id.toString().padStart(8)} " +
                    "${item.typeName.padEnd(6)} " +
                    "${formatRelativeTime(item.createTime).padEnd(8)} " +
                    preview,
            )
        }
    }
}

private fun encodeQuery(query: String): String = query.replace(" ", "%20").replace("&", "%26").replace("=", "%3D")
