package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
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
    /** Human-readable summary (HTML/RTF converted to plain text). */
    val content: String?,
    /** Content exactly as stored (HTML/RTF keep their source markup). */
    val rawContent: String? = null,
)

class PasteCommand : CliktCommand(name = "paste") {

    override fun help(context: Context): String = "Show the most recent paste, or a specific paste by ID"

    private val ctx by requireObject<CliContext>()

    private val id by argument(help = "Paste ID to show (omit for most recent)").long().optional()

    private val raw by option(
        "--raw",
        "-r",
        help =
            "Print only the paste content, exactly as stored — HTML/RTF print their source " +
                "markup (for piping, e.g. `crosspaste paste -r | pbcopy`)",
    ).flag()

    private val summary by option(
        "--summary",
        "-s",
        help = "Print only the plain-text summary of the paste content (HTML/RTF converted to text)",
    ).flag()

    private val noNewline by option(
        "--no-newline",
        help = "With --raw or --summary: do not append a trailing newline",
    ).flag()

    override fun run() {
        if (raw && summary) {
            throw usageError("--raw and --summary are mutually exclusive")
        }
        if (noNewline && !raw && !summary) {
            throw usageError("--no-newline requires --raw or --summary")
        }
        runCli { client ->
            val path = id?.let { "/cli/paste/$it" } ?: "/cli/paste/latest"
            val detail = client.getBody(path, PasteDetailResponse.serializer())

            when {
                raw -> printContentOnly(detail, detail.rawContent)
                summary -> printContentOnly(detail, detail.content)
                ctx.json -> echo(cliJson.encodeToString(PasteDetailResponse.serializer(), detail))
                else -> printDetail(detail)
            }
        }
    }

    /**
     * Bypasses the Mordant terminal on purpose: it re-renders strings (and
     * strips ANSI codes on non-TTY output), while content-only modes must
     * reproduce the stored content byte for byte.
     */
    private fun printContentOnly(
        detail: PasteDetailResponse,
        content: String?,
    ) {
        if (content == null) {
            echo("Error: paste #${detail.id} (${detail.typeName}) has no content.", err = true)
            throw ProgramResult(1)
        }
        if (noNewline) {
            print(content)
        } else {
            println(content)
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
