package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.crosspaste.cli.api.CliClientException
import com.crosspaste.cli.platform.FALLBACK_EDITOR
import com.crosspaste.cli.platform.createEditTempDir
import com.crosspaste.cli.platform.readPlatformEnv
import com.crosspaste.cli.platform.runEditor
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.serialization.Serializable
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.SYSTEM

class EditCommand : CliktCommand(name = "edit") {

    override fun help(context: Context): String =
        "Edit a paste in \$EDITOR and update it in place (omit ID for the most recent)"

    override fun helpEpilog(context: Context): String =
        "The paste keeps its id, type, and tags: HTML/RTF edit their source markup " +
            "and stay HTML/RTF, links edit the URL, colors edit the #RRGGBBAA value. " +
            "Like editing inside the app, the change is not re-synced to devices " +
            "that already received the original."

    private val ctx by requireObject<CliContext>()

    private val id by argument(help = "Paste ID to edit (omit for most recent)").long().optional()

    override fun run() {
        val info = terminal.terminalInfo
        if (!info.inputInteractive || !info.outputInteractive) {
            throw usageError("edit requires an interactive terminal")
        }
        editPasteFlow(id, jsonOutput = ctx.json)
    }
}

/**
 * The whole editor round-trip, shared by `edit` and `pick`'s Ctrl-E. The
 * caller must have verified an interactive terminal first.
 */
internal fun CliktCommand.editPasteFlow(
    id: Long?,
    jsonOutput: Boolean,
) {
    val editor = resolveEditor(::readPlatformEnv) ?: FALLBACK_EDITOR
    val detail = fetchPasteForEdit(id)
    val original =
        editableContent(detail) ?: run {
            echo(
                "Error: paste #${detail.id} (${detail.typeName}) cannot be edited as text.",
                err = true,
            )
            throw ProgramResult(1)
        }
    // A fresh private directory per edit (0700 on POSIX, random name on
    // both) — see createEditTempDir for the threat model
    val tempDir =
        createEditTempDir() ?: run {
            echo("Error: could not create a private temporary directory for editing.", err = true)
            throw ProgramResult(1)
        }
    try {
        editRoundTrip(editor, tempDir / editTempFileName(detail.id, detail.typeName), detail.id, original, jsonOutput)
    } finally {
        cleanupBestEffort(tempDir)
    }
}

private fun CliktCommand.fetchPasteForEdit(id: Long?): PasteDetailResponse {
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
 * update are separate [runCli] rounds): an edit can take minutes, and the
 * app restarting meanwhile should not fail the save.
 */
private fun CliktCommand.editRoundTrip(
    editor: String,
    tempFile: Path,
    pasteId: Long,
    original: String,
    jsonOutput: Boolean,
) {
    writeOrFail(tempFile, original)
    val status = runEditor(editor, tempFile.toString())
    if (status != 0) {
        echo("Error: editor '$editor' failed (status $status); paste left as is.", err = true)
        throw ProgramResult(1)
    }
    val edited = normalizeEditedContent(original, readBackOrFail(tempFile))
    when {
        edited == original -> echo("No changes; paste left as is.")
        edited.isEmpty() -> {
            // An emptied buffer reads as "abort", like an empty commit message
            echo("Error: edited content is empty; paste left as is.", err = true)
            throw ProgramResult(1)
        }
        else -> updatePasteInPlace(pasteId, edited, jsonOutput)
    }
}

@Serializable
internal data class PasteUpdateRequest(
    val content: String,
)

/**
 * Writes the edited content back onto the same paste via PUT /cli/paste/{id}.
 * The row keeps its id, type, and tags — HTML/RTF stay HTML/RTF.
 */
private fun CliktCommand.updatePasteInPlace(
    id: Long,
    content: String,
    jsonOutput: Boolean,
) {
    runCli { client ->
        try {
            val body = cliJson.encodeToString(PasteUpdateRequest.serializer(), PasteUpdateRequest(content))
            val message = client.putBody("/cli/paste/$id", body, MessageResponse.serializer())
            if (jsonOutput) {
                echo(cliJson.encodeToString(MessageResponse.serializer(), message))
            } else {
                echo(message.message)
            }
        } catch (e: CliClientException) {
            // A 404 with no server message means the route itself is missing:
            // the running app predates in-place editing
            if (e.statusCode == 404 && !e.hasServerMessage) {
                echo(
                    "Error: the running CrossPaste app does not support editing " +
                        "pastes in place; please update the app.",
                    err = true,
                )
                throw ProgramResult(1)
            }
            throw e
        }
    }
}

private fun CliktCommand.writeOrFail(
    file: Path,
    content: String,
) {
    try {
        FileSystem.SYSTEM.write(file) { writeUtf8(content) }
    } catch (e: IOException) {
        echo("Error: cannot write the edit file $file: ${e.message}", err = true)
        throw ProgramResult(1)
    }
}

private fun CliktCommand.readBackOrFail(file: Path): String =
    try {
        FileSystem.SYSTEM.read(file) { readUtf8() }
    } catch (e: IOException) {
        // e.g. the editor deleted or renamed the file instead of saving it
        echo("Error: cannot read the edited file back from $file: ${e.message}", err = true)
        throw ProgramResult(1)
    }

/** Never throws: cleanup must not mask the command's real outcome. */
private fun cleanupBestEffort(dir: Path) {
    runCatching { FileSystem.SYSTEM.deleteRecursively(dir, mustExist = false) }
}

/** VISUAL wins over EDITOR (the POSIX convention); blank values are ignored. */
internal fun resolveEditor(env: (String) -> String?): String? =
    env("VISUAL")?.takeIf { it.isNotBlank() }
        ?: env("EDITOR")?.takeIf { it.isNotBlank() }

/**
 * html/rtf keep their source extension so editors highlight the markup. The
 * name only needs to be readable — uniqueness comes from the per-edit temp
 * directory around it.
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
 * The explicit allowlist of text-like paste types: unknown or future types
 * must be rejected rather than edited as text by default. Shared with pick's
 * Ctrl-E gate so the two can never drift apart.
 */
internal val EDITABLE_PASTE_TYPES = setOf("text", "link", "html", "rtf", "color")

/**
 * Returns the text to edit, or null for pastes that are not editable as text.
 * html/rtf edit their raw markup when the app exposes it.
 */
internal fun editableContent(detail: PasteDetailResponse): String? {
    if (detail.typeName !in EDITABLE_PASTE_TYPES) return null
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
