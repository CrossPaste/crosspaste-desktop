package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.crosspaste.cli.api.CliClientException
import com.crosspaste.cli.platform.flushStdout
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import io.ktor.http.*

enum class WatchFormat { LINE, JSON, ID }

/** An explicit --format wins; the global --json flag implies json; else line. */
internal fun resolveWatchFormat(
    explicit: WatchFormat?,
    json: Boolean,
): WatchFormat = explicit ?: if (json) WatchFormat.JSON else WatchFormat.LINE

internal fun buildWatchQuery(
    types: List<String>,
    tag: String?,
): String {
    val params =
        buildList {
            types.forEach { add("type=${it.encodeURLParameter()}") }
            tag?.let { add("tag=${it.encodeURLParameter()}") }
        }
    return if (params.isEmpty()) "" else params.joinToString("&", prefix = "?")
}

class WatchCommand : CliktCommand(name = "watch") {

    override fun help(context: Context): String =
        "Stream new pastes as they arrive, including entries synced from other devices. " +
            "Runs until interrupted; existing history is not replayed."

    override fun helpEpilog(context: Context): String =
        "The default output is one sanitized line per paste (its text content; " +
            "for links, the URL), ready for pipes: " +
            "`watch --type link | xargs -n1 open`. Use --format json for the full " +
            "metadata as one JSON object per line, or --format id with " +
            "`paste --raw` to fetch exact bytes."

    private val ctx by requireObject<CliContext>()

    private val types by option(
        "--type",
        "-t",
        help = "Only stream these types (text, link, image, file, html, rtf, color); repeat for several",
    ).multiple()

    private val tag by option("--tag", "-g", help = "Only stream pastes carrying this tag")

    private val format by option(
        "--format",
        help =
            "Output format: line (default, one sanitized content line per paste), " +
                "json (one JSON object per line), or id (one paste ID per line)",
    ).choice(
        "line" to WatchFormat.LINE,
        "json" to WatchFormat.JSON,
        "id" to WatchFormat.ID,
    )

    override fun run() =
        runCli { client ->
            val resolvedFormat = resolveWatchFormat(format, ctx.json)
            try {
                client.streamLines("/cli/watch${buildWatchQuery(types, tag)}") { line ->
                    emitEvent(line, resolvedFormat)
                }
            } catch (e: CliClientException) {
                // A 404 with no server message means the route itself is
                // missing: the running app predates the watch endpoint
                if (e.statusCode == 404 && !e.hasServerMessage) {
                    echo(
                        "Error: the running CrossPaste app does not support 'watch'; " +
                            "please update the app.",
                        err = true,
                    )
                    throw ProgramResult(1)
                }
                throw e
            }
        }

    private fun emitEvent(
        line: String,
        format: WatchFormat,
    ) {
        when (format) {
            // Pass the server's compact JSON through untouched: kotlinx
            // escapes control characters inside JSON strings, so the line is
            // terminal-safe as-is and re-encoding could only lose fidelity
            WatchFormat.JSON -> println(line)
            WatchFormat.ID, WatchFormat.LINE -> {
                val item =
                    runCatching {
                        cliJson.decodeFromString(PasteSummaryDto.serializer(), line)
                    }.getOrElse {
                        echo("Warning: skipped an unparseable watch event.", err = true)
                        return
                    }
                when (format) {
                    WatchFormat.ID -> println(item.id)
                    else -> println(collapsePreviewWhitespace(item.preview))
                }
            }
        }
        // Events must reach a pipe as they happen, not when the CRT buffer
        // fills; stdlib print instead of Mordant keeps non-TTY output verbatim
        flushStdout()
    }
}
