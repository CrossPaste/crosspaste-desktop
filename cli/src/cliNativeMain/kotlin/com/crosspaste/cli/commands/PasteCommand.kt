package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.crosspaste.cli.api.AppNotRunningException
import com.crosspaste.cli.api.CLI_IMAGE_MAX_BOX_PX
import com.crosspaste.cli.api.CliClient
import com.crosspaste.cli.api.CliClientException
import com.crosspaste.cli.platform.resolveTerminalImageDisplay
import com.crosspaste.cli.platform.sixelPixelBox
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.terminal
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
    /** Absolute paths of stored payload files (image/file pastes only). */
    val filePaths: List<String> = emptyList(),
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
                "markup, image pastes dump the image bytes (for piping, e.g. " +
                "`crosspaste paste -r | pbcopy` or `crosspaste paste -r > shot.png`)",
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
            val basePath = id?.let { "/cli/paste/$it" } ?: "/cli/paste/latest"
            // Raw markup is opt-in server-side: for plain text raw == summary,
            // and always shipping both would double large payloads
            val path = if (raw) "$basePath?includeRaw=true" else basePath
            val detail = client.getBody(path, PasteDetailResponse.serializer())

            when {
                raw && detail.typeName == "image" -> printRawImage(detail)
                raw -> printRawContent(detail)
                summary -> printContentOnly(detail, detail.content)
                ctx.json -> echo(cliJson.encodeToString(PasteDetailResponse.serializer(), detail))
                else -> printDetail(client, detail)
            }
        }
    }

    /**
     * A missing rawContent while the summary exists means the app predates
     * the includeRaw capability (it ignored ?includeRaw=true) — that deserves
     * an actionable message, not "has no content".
     */
    private fun printRawContent(detail: PasteDetailResponse) {
        if (detail.rawContent == null && detail.content != null) {
            echo(
                "Error: the running CrossPaste app does not support --raw yet; " +
                    "update (or restart) the app, or use --summary instead.",
                err = true,
            )
            throw ProgramResult(1)
        }
        printContentOnly(detail, detail.rawContent)
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

    /**
     * `--raw` on an image paste dumps the stored image bytes (for piping:
     * `crosspaste paste --raw > shot.png`); the file is read directly since
     * app and CLI share the machine. Text pastes keep the string path.
     */
    private fun printRawImage(detail: PasteDetailResponse) {
        when (val action = resolveRawImageAction(detail.filePaths)) {
            RawImageAction.MissingPaths -> {
                echo(
                    "Error: the running CrossPaste app does not expose image file paths yet; " +
                        "update (or restart) the app.",
                    err = true,
                )
                throw ProgramResult(1)
            }
            is RawImageAction.TooManyImages -> {
                echo(
                    "Error: paste #${detail.id} contains ${action.paths.size} images; " +
                        "--raw needs a single image. Paths:",
                    err = true,
                )
                action.paths.forEach { echo("  $it", err = true) }
                throw ProgramResult(1)
            }
            is RawImageAction.StreamSingle ->
                try {
                    ImageByteStreamer().stream(action.path)
                } catch (e: okio.IOException) {
                    echo(
                        "Error: cannot read the stored image of paste #${detail.id}: ${e.message}",
                        err = true,
                    )
                    throw ProgramResult(1)
                }
        }
    }

    private suspend fun printDetail(
        client: CliClient,
        detail: PasteDetailResponse,
    ) {
        val fav = if (detail.tagged) " [tagged]" else ""
        val remote = if (detail.remote) " (remote)" else ""
        echo("Paste #${detail.id}$fav$remote")
        echo("  Type:    ${detail.typeName}")
        echo("  Source:  ${detail.source ?: "-"}")
        echo("  Size:    ${formatSize(detail.size)}")
        echo("  Time:    ${formatRelativeTime(detail.createTime)}")
        echo("  Hash:    ${detail.hash}")
        if (detail.filePaths.isNotEmpty()) {
            echo("  Files:")
            detail.filePaths.forEach { echo("    $it") }
        }
        if (detail.content != null && detail.filePaths.isEmpty()) {
            echo("  Content:")
            detail.content.lines().forEach { line ->
                echo("    $line")
            }
        }
        if (detail.typeName == "image" && terminal.terminalInfo.outputInteractive) {
            renderInlineImages(client, detail)
        }
    }

    /**
     * Inline preview in terminals with an image protocol; anywhere else the
     * absolute paths printed above are the fallback. Escape sequences go
     * through stdlib print like [printContentOnly]: Mordant re-renders
     * strings and would mangle them.
     *
     * Sixel candidates run the DA1 probe inside [resolveTerminalImageDisplay]
     * here (stdout is interactive per the caller's guard; the probe also
     * requires interactive stdin). Sixel pixels come from the app's transcode
     * endpoint at final display size: the terminal width in cells times the
     * probed cell pixel width, height capped only by the endpoint's box clamp
     * — tall images scroll like text, matching the other protocols' behavior.
     */
    private suspend fun renderInlineImages(
        client: CliClient,
        detail: PasteDetailResponse,
    ) {
        val info = terminal.terminalInfo
        val display =
            resolveTerminalImageDisplay(info.inputInteractive, info.outputInteractive) ?: return
        // Rows = the endpoint's box clamp in cells: with any cell height the
        // pixel height saturates at CLI_IMAGE_MAX_BOX_PX, i.e. "as tall as
        // the endpoint allows" — paste deliberately has no row limit
        val (sixelMaxWidthPx, sixelMaxHeightPx) =
            sixelPixelBox(terminal.size.width, CLI_IMAGE_MAX_BOX_PX, display)
        InlineImageRenderer(
            protocol = display.protocol,
            emit = { print(it) },
            note = { echo("  $it") },
            sixelFetch = { index ->
                // Any failure — including 404 from an app that predates the
                // endpoint — falls back to the paths printed above. The one
                // exception: a 503 with a structured message is the app saying
                // its decode backend is down (e.g. missing libGL on a headless
                // server, #4854) — that remedy must reach the user, not vanish
                // into a silent fallback.
                try {
                    client.getRawImage(detail.id, index, sixelMaxWidthPx, sixelMaxHeightPx)
                } catch (e: CliClientException) {
                    if (e.statusCode == 503 && e.hasServerMessage) {
                        throw PreviewUnavailableException(e.message ?: "Image preview unavailable.")
                    }
                    null
                } catch (_: AppNotRunningException) {
                    null
                }
            },
        ).render(detail.filePaths)
    }
}
