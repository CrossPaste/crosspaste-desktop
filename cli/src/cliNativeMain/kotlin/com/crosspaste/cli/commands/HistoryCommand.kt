package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
data class PasteSummaryDto(
    val id: Long,
    val typeName: String,
    val source: String?,
    val size: Long,
    val tagged: Boolean,
    val createTime: Long,
    val preview: String,
    val remote: Boolean,
)

@Serializable
data class PasteListResponse(
    val items: List<PasteSummaryDto>,
    val total: Long,
)

enum class ListFormat { TABLE, JSON, ID }

/** Shared --format option for the list-producing commands (history, search). */
fun ParameterHolder.listFormatOption() =
    option(
        "--format",
        help = "Output format: table (default), json, or id (one paste ID per line, for piping)",
    ).choice(
        "table" to ListFormat.TABLE,
        "json" to ListFormat.JSON,
        "id" to ListFormat.ID,
    )

/** An explicit --format wins; the global --json flag implies json; else table. */
fun resolveListFormat(
    explicit: ListFormat?,
    json: Boolean,
): ListFormat = explicit ?: if (json) ListFormat.JSON else ListFormat.TABLE

fun buildListQuery(
    limit: Int,
    type: String?,
    tag: String?,
    query: String? = null,
): String {
    val params =
        buildList {
            query?.let { add("q=${it.encodeURLParameter()}") }
            add("limit=$limit")
            type?.let { add("type=${it.encodeURLParameter()}") }
            tag?.let { add("tag=${it.encodeURLParameter()}") }
        }
    return params.joinToString("&", prefix = "?")
}

class HistoryCommand : CliktCommand(name = "history") {

    override fun help(context: Context): String = "List recent paste history"

    private val ctx by requireObject<CliContext>()

    private val limit by option("--limit", "-n", help = "Number of items to show").int().default(20)

    private val type by option("--type", "-t", help = "Filter by type (text, link, image, file, html, rtf, color)")

    private val tag by option("--tag", "-g", help = "Filter by tag name")

    private val format by listFormatOption()

    override fun run() =
        runCli { client ->
            val list =
                client.getBody(
                    "/cli/history${buildListQuery(limit, type, tag)}",
                    PasteListResponse.serializer(),
                )

            when (resolveListFormat(format, ctx.json)) {
                ListFormat.JSON -> echo(cliJson.encodeToString(PasteListResponse.serializer(), list))
                ListFormat.ID -> list.items.forEach { echo(it.id) }
                ListFormat.TABLE -> printList(list)
            }
        }

    private fun printList(list: PasteListResponse) {
        if (list.items.isEmpty()) {
            echo("No pastes found.")
            return
        }
        echo("${list.items.size} of ${list.total} pastes:")
        echo("")
        printPasteRows(list.items, showRemote = true, showSize = true)
    }
}
