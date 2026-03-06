package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.crosspaste.db.paste.PasteDao
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.serialization.Serializable

@Serializable
data class PasteDetailResponse(
    val id: Long,
    val typeName: String,
    val source: String?,
    val size: Long,
    val tagged: Boolean,
    val createTime: Long,
    val remote: Boolean,
    val hash: String,
    val content: String?,
)

class PasteCommand : CliktCommand(name = "paste") {

    override fun help(context: Context): String = "Show the most recent paste, or a specific paste by ID"

    private val ctx by requireObject<CliContext>()

    private val id by argument(help = "Paste ID to show (omit for most recent)").long().optional()

    override fun run() =
        runWithDao {
            val pasteDao = getDao<PasteDao>()
            val pasteData =
                if (id != null) {
                    pasteDao.getNoDeletePasteData(id!!)
                } else {
                    pasteDao.getLatestLoadedPasteData()
                }

            if (pasteData == null) {
                echo("Paste not found.", err = true)
                return@runWithDao
            }

            val detail = pasteData.toDetailResponse()
            if (ctx.json) {
                echo(cliJson.encodeToString(PasteDetailResponse.serializer(), detail))
            } else {
                printDetail(detail)
            }
        }

    private fun printDetail(detail: PasteDetailResponse) {
        val fav = if (detail.tagged) " [tagged]" else ""
        val remote = if (detail.remote) " (remote)" else ""
        echo("Paste #${detail.id}$fav$remote")
        echo("  Type:    ${detail.typeName}")
        echo("  Source:  ${detail.source ?: "-"}")
        echo("  Size:    ${formatSize(detail.size)}")
        echo("  Time:    ${formatRelativeTime(detail.createTime)}")
        echo("  Hash:    ${detail.hash}")
        if (detail.content != null) {
            echo("  Content:")
            detail.content.lines().forEach { line ->
                echo("    $line")
            }
        }
    }
}
