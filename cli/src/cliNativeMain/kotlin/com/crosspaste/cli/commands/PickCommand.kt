package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.crosspaste.cli.api.CliClient
import com.crosspaste.cli.api.CliClientException
import com.crosspaste.cli.cliCommandName
import com.crosspaste.cli.commands.pick.PREVIEW_AUTO_OPEN_MIN_HEIGHT
import com.crosspaste.cli.commands.pick.PickFilters
import com.crosspaste.cli.commands.pick.PickOutcome
import com.crosspaste.cli.commands.pick.PickState
import com.crosspaste.cli.commands.pick.PickTui
import com.crosspaste.cli.platform.installTerminalGuard
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.mordant.rendering.TextColors
import kotlinx.serialization.builtins.ListSerializer

/** fzf convention: cancelling an interactive picker exits 130. */
const val EXIT_CODE_CANCELLED = 130

class PickCommand : CliktCommand(name = "pick") {

    override fun help(context: Context): String = "Interactively search paste history and copy the selected paste"

    private val ctx by requireObject<CliContext>()

    private val query by argument(
        name = "query",
        help = "Initial search words (same as typing them in the picker)",
    ).multiple()

    private val limit by option(
        "--limit",
        "-n",
        help = "Size of the fetched history window",
    ).int().default(100)

    private val types by option(
        "--type",
        "-t",
        help = "Initial type filter (text, link, image, file, html, rtf, color); repeat for several",
    ).multiple()

    private val tag by option("--tag", "-g", help = "Initial tag filter")

    private val sort by option(
        "--sort",
        "-s",
        help = "Sort by creation time: newest (default) or oldest first",
    ).choice(SORT_NEWEST, SORT_OLDEST)

    /**
     * The interactive state survives across TUI sessions: Ctrl-E suspends the
     * picker for an editor round-trip and re-enters it with the query,
     * filters, selection, and preview toggle intact (only the data window is
     * re-fetched by the new session).
     */
    private var sessionState: PickState? = null

    override fun run() {
        val info = terminal.terminalInfo
        if (!info.inputInteractive || !info.outputInteractive) {
            throw usageError(
                "pick requires an interactive terminal; " +
                    "for pipes use: $cliCommandName history --format id | fzf",
            )
        }
        // External SIGTERM/console-close must not strand the shell in raw
        // mode on the alternate screen (in-band Ctrl-C is a key event)
        installTerminalGuard()
        while (true) {
            when (val outcome = runPickSession()) {
                is PickOutcome.Copied -> return
                is PickOutcome.Edit -> editPasteFlow(outcome.item.id, jsonOutput = ctx.json)
                PickOutcome.Cancelled -> throw ProgramResult(EXIT_CODE_CANCELLED)
                null -> return
            }
        }
    }

    private fun runPickSession(): PickOutcome? {
        var outcome: PickOutcome? = null
        runCli { client ->
            val state = sessionState ?: createState(client).also { sessionState = it }
            val result = PickTui(terminal, client, state, limit).run()
            if (result is PickOutcome.Copied) {
                val response = copyById(client, result.item.id)
                reportCopied(result, response)
            }
            outcome = result
        }
        return outcome
    }

    private suspend fun createState(client: CliClient): PickState {
        val tags =
            runCatching {
                client.getBody("/cli/tags", ListSerializer(TagSummary.serializer())).map { it.name }
            }.getOrDefault(emptyList())
        return PickState(
            // argv can carry control chars; the query is echoed raw
            query = sanitizeTerminalText(query.joinToString(" ")),
            filters =
                PickFilters(
                    // Normalized so the selector bar and the type cycle
                    // recognize the values regardless of argv casing
                    types = types.map { it.lowercase() },
                    tag = tag?.let { given -> tags.firstOrNull { it.equals(given, ignoreCase = true) } ?: given },
                    sortNewest = sort != SORT_OLDEST,
                ),
            tagNames = tags,
        ).also {
            it.previewOpen = terminal.size.height >= PREVIEW_AUTO_OPEN_MIN_HEIGHT
        }
    }

    /**
     * A 404 without a server message means the route itself is missing — the
     * running app predates POST /cli/paste/{id}/copy. That deserves an
     * actionable message, not a raw status code. A genuinely missing paste
     * 404s WITH a structured server message ("Paste #n not found.") and is
     * reported as-is — the distinction rides on [CliClientException.statusCode]
     * and [CliClientException.hasServerMessage], never on message text.
     */
    private suspend fun copyById(
        client: CliClient,
        id: Long,
    ): MessageResponse =
        try {
            client.postBody("/cli/paste/$id/copy", "", MessageResponse.serializer())
        } catch (e: CliClientException) {
            if (e.statusCode == 404 && !e.hasServerMessage) {
                throw CliClientException(
                    "the running CrossPaste app does not support pick's copy yet; " +
                        "update (or restart) the app.",
                )
            }
            throw e
        }

    private fun reportCopied(
        copied: PickOutcome.Copied,
        response: MessageResponse,
    ) {
        val item = copied.item
        if (ctx.json) {
            echo(cliJson.encodeToString(MessageResponse.serializer(), response))
            return
        }
        // No --print-id / stdout capture mode yet: the TUI renders on stdout,
        // so `$(crosspaste pick ...)` would trip the TTY guard. Enabling that
        // needs an fzf-style stderr UI — tracked as a follow-up.
        echo(TextColors.green("✔") + " Copied #${item.id} (${item.typeName}, ${formatSize(item.size)})")
    }
}
