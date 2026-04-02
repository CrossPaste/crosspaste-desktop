package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.paste.PasteTagDao
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
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

class HistoryCommand : CliktCommand(name = "history") {

    override fun help(context: Context): String = "List recent paste history"

    private val ctx by requireObject<CliContext>()

    private val limit by option("--limit", "-n", help = "Number of items to show").int().default(20)

    private val type by option("--type", "-t", help = "Filter by type (text, link, image, file, html, rtf, color)")

    private val tag by option("--tag", "-g", help = "Filter by tag name")

    override fun run() =
        runWithDao {
            val pasteDao = getDao<PasteDao>()
            val pasteTagDao = getDao<PasteTagDao>()
            val pasteTypeList = listOfNotNull(resolveTypeFilter(type))

            val tagId: Long? =
                tag?.let { name ->
                    pasteTagDao
                        .getAllTagsBlock()
                        .firstOrNull { it.name.equals(name, ignoreCase = true) }
                        ?.id
                }

            val results =
                pasteDao.searchPasteData(
                    searchTerms = listOf(),
                    pasteTypeList = pasteTypeList,
                    tag = tagId,
                    limit = limit,
                )
            val total = pasteDao.getActiveCount()
            val list =
                PasteListResponse(
                    items = results.map { it.toSummaryDto() },
                    total = total,
                )

            if (ctx.json) {
                echo(cliJson.encodeToString(PasteListResponse.serializer(), list))
            } else {
                printList(list)
            }
        }

    private fun printList(list: PasteListResponse) {
        if (list.items.isEmpty()) {
            echo("No pastes found.")
            return
        }
        echo("${list.items.size} of ${list.total} pastes:")
        echo("")
        for (item in list.items) {
            val fav = if (item.tagged) "*" else " "
            val remote = if (item.remote) "R" else "L"
            val preview = item.preview.replace("\n", " ").take(60)
            echo(
                "$fav ${item.id.toString().padStart(8)} " +
                    "${item.typeName.padEnd(6)} " +
                    "$remote " +
                    "${formatRelativeTime(item.createTime).padEnd(8)} " +
                    "${formatSize(item.size).padEnd(6)} " +
                    preview,
            )
        }
    }
}
