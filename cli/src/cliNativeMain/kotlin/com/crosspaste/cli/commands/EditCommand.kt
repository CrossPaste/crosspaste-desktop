package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.crosspaste.cli.platform.FALLBACK_EDITOR
import com.crosspaste.cli.platform.runEditorCommand
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM

class EditCommand : CliktCommand(name = "edit") {

    override fun help(context: Context): String =
        "Edit a paste in \$EDITOR and copy the result as a new text paste (omit ID for the most recent)"

    private val ctx by requireObject<CliContext>()

    private val id by argument(help = "Paste ID to edit (omit for most recent)").long().optional()

    override fun run() {
        val info = terminal.terminalInfo
        if (!info.inputInteractive || !info.outputInteractive) {
            throw usageError("edit requires an interactive terminal")
        }
        val editor = resolveEditor(::readEnv) ?: FALLBACK_EDITOR
        val detail = fetchPaste()
        val original =
            editableContent(detail) ?: run {
                echo(
                    "Error: paste #${detail.id} (${detail.typeName}) cannot be edited as text.",
                    err = true,
                )
                throw ProgramResult(1)
            }
        val tempFile = tempDirectory(::readEnv) / editTempFileName(detail.id, detail.typeName)
        editRoundTrip(editor, tempFile, original)
    }

    private fun fetchPaste(): PasteDetailResponse {
        var detail: PasteDetailResponse? = null
        runCli { client ->
            val basePath = id?.let { "/cli/paste/$it" } ?: "/cli/paste/latest"
            // Raw markup opt-in mirrors `paste --raw`: html/rtf edit their
            // source, and old apps that ignore the flag fall back to summary
            detail = client.getBody("$basePath?includeRaw=true", PasteDetailResponse.serializer())
        }
        // runCli only returns normally after the block succeeded
        return detail!!
    }

    /**
     * The HTTP client is NOT held open across the editor session (fetch and
     * copy are separate [runCli] rounds): an edit can take minutes, and the
     * app restarting meanwhile should not fail the save.
     */
    private fun editRoundTrip(
        editor: String,
        tempFile: Path,
        original: String,
    ) {
        val fs = FileSystem.SYSTEM
        fs.write(tempFile) { writeUtf8(original) }
        try {
            val status = runEditorCommand(buildEditorCommand(editor, tempFile.toString()))
            if (status != 0) {
                echo("Error: editor '$editor' failed (status $status); nothing copied.", err = true)
                throw ProgramResult(1)
            }
            val edited = normalizeEditedContent(original, fs.read(tempFile) { readUtf8() })
            when {
                edited == original -> echo("No changes; nothing copied.")
                edited.isEmpty() -> {
                    // An emptied buffer reads as "abort", like an empty commit message
                    echo("Error: edited content is empty; nothing copied.", err = true)
                    throw ProgramResult(1)
                }
                else -> copyToCrossPaste(edited, jsonOutput = ctx.json)
            }
        } finally {
            fs.delete(tempFile, mustExist = false)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun readEnv(name: String): String? = platform.posix.getenv(name)?.toKString()

/**
 * okio's native targets have no SYSTEM_TEMPORARY_DIRECTORY, so the temp dir
 * comes from the environment: TMPDIR (posix) then TEMP/TMP (Windows, where
 * TEMP is always set), falling back to /tmp.
 */
internal fun tempDirectory(env: (String) -> String?): Path =
    (env("TMPDIR") ?: env("TEMP") ?: env("TMP") ?: "/tmp").toPath(normalize = true)

/** VISUAL wins over EDITOR (the POSIX convention); blank values are ignored. */
internal fun resolveEditor(env: (String) -> String?): String? =
    env("VISUAL")?.takeIf { it.isNotBlank() }
        ?: env("EDITOR")?.takeIf { it.isNotBlank() }

/**
 * $VISUAL/$EDITOR may carry arguments ("code --wait"), so the value is passed
 * to the shell as-is and only the file path is quoted.
 */
internal fun buildEditorCommand(
    editor: String,
    filePath: String,
): String = "$editor \"$filePath\""

/**
 * html/rtf keep their source extension so editors highlight the markup. The
 * name is stable per paste ID: concurrent edits of the same paste share (and
 * overwrite) one temp file, which is acceptable.
 */
internal fun editTempFileName(
    id: Long,
    typeName: String,
): String {
    val extension =
        when (typeName) {
            "html" -> "html"
            "rtf" -> "rtf"
            else -> "txt"
        }
    return "crosspaste-edit-$id.$extension"
}

/**
 * Returns the text to edit, or null for pastes that are not editable as text
 * (images, files). html/rtf edit their raw markup when the app exposes it.
 */
internal fun editableContent(detail: PasteDetailResponse): String? {
    if (detail.typeName == "image" || detail.typeName == "file") return null
    return detail.rawContent ?: detail.content
}

/**
 * Editors like vi always terminate the file with a newline; when the original
 * had none, that editor-added trailing newline (LF or CRLF) is stripped so a
 * save-without-changes round-trip is recognized as "no changes" and an actual
 * edit doesn't grow an invisible trailing newline.
 */
internal fun normalizeEditedContent(
    original: String,
    edited: String,
): String {
    if (original.endsWith("\n")) return edited
    return when {
        edited.endsWith("\r\n") -> edited.dropLast(2)
        edited.endsWith("\n") -> edited.dropLast(1)
        else -> edited
    }
}
