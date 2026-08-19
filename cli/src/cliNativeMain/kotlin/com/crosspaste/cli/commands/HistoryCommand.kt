package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
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

/** The --format option for list-producing commands. */
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

const val SORT_NEWEST = "newest"
const val SORT_OLDEST = "oldest"

fun buildListQuery(
    limit: Int,
    types: List<String>,
    tag: String?,
    sort: String? = null,
    query: String? = null,
): String {
    val params =
        buildList {
            query?.let { add("q=${it.encodeURLParameter()}") }
            add("limit=$limit")
            // Repeated for OR-matching, mirroring the search window's multi-type filter
            types.forEach { add("type=${it.encodeURLParameter()}") }
            tag?.let { add("tag=${it.encodeURLParameter()}") }
            sort?.let { add("sort=${it.encodeURLParameter()}") }
        }
    return params.joinToString("&", prefix = "?")
}

class HistoryCommand : CliktCommand(name = "history") {

    override fun help(context: Context): String = "List or search paste history"

    private val ctx by requireObject<CliContext>()

    private val query by argument(
        name = "query",
        help = "Search words; without them the newest pastes are listed",
    ).multiple()

    private val limit by option("--limit", "-n", help = "Number of items to show").int().default(20)

    private val types by option(
        "--type",
        "-t",
        help = "Filter by type (text, link, image, file, html, rtf, color); repeat to match any of several",
    ).multiple()

    private val tag by option("--tag", "-g", help = "Filter by tag name")

    private val sort by option(
        "--sort",
        "-s",
        help = "Sort by creation time: newest (default) or oldest first",
    ).choice(SORT_NEWEST, SORT_OLDEST)

    private val format by listFormatOption()

    override fun run() =
        runCli { client ->
            val list =
                client.getBody(
                    "/cli/history${buildListQuery(limit, types, tag, sort, searchQuery())}",
                    PasteListResponse.serializer(),
                )

            when (resolveListFormat(format, ctx.json)) {
                ListFormat.JSON -> echo(cliJson.encodeToString(PasteListResponse.serializer(), list))
                ListFormat.ID -> list.items.forEach { echo(it.id) }
                ListFormat.TABLE -> printList(list)
            }
        }

    private fun searchQuery(): String? = query.joinToString(" ").ifBlank { null }

    private fun printList(list: PasteListResponse) {
        if (list.items.isEmpty()) {
            val q = searchQuery()
            echo(if (q == null) "No pastes found." else "No results found for \"$q\".")
            return
        }
        echo("${list.items.size} of ${list.total} pastes:")
        echo("")
        printPasteRows(list.items, showRemote = true, showSize = true)
    }
}
