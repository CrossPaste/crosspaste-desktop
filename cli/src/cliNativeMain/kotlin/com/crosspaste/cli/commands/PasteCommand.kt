package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.crosspaste.cli.platform.TerminalImageProtocol
import com.crosspaste.cli.platform.buildItermInlineImage
import com.crosspaste.cli.platform.buildKittyInlineImage
import com.crosspaste.cli.platform.detectTerminalImageProtocol
import com.crosspaste.cli.platform.flushStdout
import com.crosspaste.cli.platform.isPng
import com.crosspaste.cli.platform.prepareStdoutForBinary
import com.crosspaste.cli.platform.writeBytesToStdout
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
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.use

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
                else -> printDetail(detail)
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
        if (detail.filePaths.isEmpty()) {
            echo(
                "Error: the running CrossPaste app does not expose image file paths yet; " +
                    "update (or restart) the app.",
                err = true,
            )
            throw ProgramResult(1)
        }
        if (detail.filePaths.size > 1) {
            echo(
                "Error: paste #${detail.id} contains ${detail.filePaths.size} images; " +
                    "--raw needs a single image. Paths:",
                err = true,
            )
            detail.filePaths.forEach { echo("  $it", err = true) }
            throw ProgramResult(1)
        }
        streamFileToStdout(detail, detail.filePaths.single())
    }

    private fun streamFileToStdout(
        detail: PasteDetailResponse,
        path: String,
    ) {
        prepareStdoutForBinary()
        try {
            FileSystem.SYSTEM.source(path.toPath()).buffer().use { source ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = source.read(buffer, 0, buffer.size)
                    if (read == -1) break
                    writeBytesToStdout(buffer, read)
                }
            }
        } catch (e: okio.IOException) {
            echo(
                "Error: cannot read the stored image of paste #${detail.id}: ${e.message}",
                err = true,
            )
            throw ProgramResult(1)
        } finally {
            flushStdout()
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
            renderInlineImages(detail)
        }
    }

    /**
     * Inline preview in terminals with an image protocol; anywhere else the
     * absolute paths printed above are the fallback. Uses stdlib print like
     * [printContentOnly]: Mordant re-renders strings and would mangle the
     * escape sequences.
     */
    private fun renderInlineImages(detail: PasteDetailResponse) {
        val protocol = detectTerminalImageProtocol() ?: return
        for (path in detail.filePaths) {
            val bytes = readImageForInline(path) ?: continue
            when (protocol) {
                TerminalImageProtocol.ITERM -> print(buildItermInlineImage(path.substringAfterLast('/'), bytes))
                TerminalImageProtocol.KITTY -> {
                    // Kitty's f=100 transfer is PNG-only; other formats keep
                    // the path fallback
                    if (!isPng(bytes)) continue
                    print(buildKittyInlineImage(bytes))
                }
            }
            println()
        }
    }

    private fun readImageForInline(path: String): ByteArray? {
        val okioPath = path.toPath()
        return try {
            val size = FileSystem.SYSTEM.metadata(okioPath).size ?: return null
            if (size > MAX_INLINE_IMAGE_BYTES) {
                echo("  (image too large for inline preview: $path)", err = true)
                return null
            }
            FileSystem.SYSTEM.read(okioPath) { readByteArray() }
        } catch (_: okio.IOException) {
            null
        }
    }

    companion object {
        /** Inline preview only; --raw streams without a cap. */
        private const val MAX_INLINE_IMAGE_BYTES = 20L * 1024 * 1024
    }
}
