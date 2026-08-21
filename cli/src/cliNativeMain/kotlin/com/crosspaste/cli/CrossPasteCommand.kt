package com.crosspaste.cli

import com.crosspaste.cli.commands.ConfigCommand
import com.crosspaste.cli.commands.CopyCommand
import com.crosspaste.cli.commands.DeleteCommand
import com.crosspaste.cli.commands.DevicesCommand
import com.crosspaste.cli.commands.EditCommand
import com.crosspaste.cli.commands.HistoryCommand
import com.crosspaste.cli.commands.PairCommand
import com.crosspaste.cli.commands.PasteCommand
import com.crosspaste.cli.commands.PickCommand
import com.crosspaste.cli.commands.StatusCommand
import com.crosspaste.cli.commands.TagsCommand
import com.crosspaste.cli.commands.TokenCommand
import com.crosspaste.cli.commands.VersionCommand
import com.crosspaste.cli.commands.WatchCommand
import com.crosspaste.cli.commands.copyToCrossPaste
import com.crosspaste.cli.commands.readPipedStdinOrFail
import com.crosspaste.cli.commands.usageError
import com.crosspaste.cli.platform.readAllStdin
import com.github.ajalt.clikt.completion.completionOption
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch
import kotlin.experimental.ExperimentalNativeApi

/**
 * The name users actually invoke, shown in usage/help output and hints. On
 * Windows the MSIX execution alias is `crosspaste-cli` (`crosspaste` is
 * hardwired by Conveyor to launch the GUI); on macOS/Linux the terminal
 * command is `crosspaste` (symlink to the bundled crosspaste-cli binary).
 */
@OptIn(ExperimentalNativeApi::class)
internal val cliCommandName: String =
    if (Platform.osFamily == OsFamily.WINDOWS) "crosspaste-cli" else "crosspaste"

class CrossPasteCommand(
    /** Injectable so tests of the implicit-copy path never touch real stdin. */
    private val stdinReader: () -> String = ::readAllStdin,
) : CliktCommand(name = cliCommandName) {

    /** Lets a bare invocation with piped stdin behave as `copy`. */
    override val invokeWithoutSubcommand: Boolean get() = true

    override fun aliases(): Map<String, List<String>> =
        mapOf(
            "c" to listOf("copy"),
            "p" to listOf("paste"),
            "h" to listOf("history"),
        )

    override fun help(context: Context): String =
        "CrossPaste CLI - interact with your local CrossPaste application. " +
            "When input is piped and no command is given, behaves as `copy`: " +
            "`git log | $cliCommandName` copies the log."

    override fun helpEpilog(context: Context): String =
        "Command aliases: c = copy, p = paste, h = history.\n\n" +
            "Exit codes: 0 success, 1 error, 2 usage error, 3 CrossPaste not running."

    val json by option("--json", help = "Output in JSON format for machine consumption").flag()

    val autoStart by option(
        help =
            "When CrossPaste is not running: --start launches it without asking, --no-start " +
                "never launches it. Default is to ask on an interactive terminal and to fail " +
                "with exit code 3 otherwise.",
    ).switch(
        "--start" to true,
        "--no-start" to false,
    )

    override fun run() {
        val cliContext = CliContext(json = json, autoStart = autoStart)
        currentContext.obj = cliContext
        if (currentContext.invokedSubcommand != null) return
        // No subcommand: piped stdin means an implicit `copy`; an interactive
        // terminal keeps the pre-invokeWithoutSubcommand usage-error behavior
        if (terminal.terminalInfo.inputInteractive) {
            throw usageError("missing command (pipe input via stdin to copy it without one)")
        }
        copyToCrossPaste(readPipedStdinOrFail(stdinReader), jsonOutput = cliContext.json)
    }

    init {
        completionOption()
        subcommands(
            StatusCommand(),
            PasteCommand(),
            HistoryCommand(),
            PickCommand(),
            CopyCommand(),
            EditCommand(),
            DeleteCommand(),
            DevicesCommand(),
            PairCommand(),
            TokenCommand(),
            ConfigCommand(),
            TagsCommand(),
            VersionCommand(),
            WatchCommand(),
        )
    }
}

data class CliContext(
    val json: Boolean = false,
    /** Tri-state launch consent: true = always start, false = never, null = ask. */
    val autoStart: Boolean? = null,
)
