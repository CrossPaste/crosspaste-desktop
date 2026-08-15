package com.crosspaste.cli

import com.crosspaste.cli.commands.ConfigCommand
import com.crosspaste.cli.commands.CopyCommand
import com.crosspaste.cli.commands.DeleteCommand
import com.crosspaste.cli.commands.DevicesCommand
import com.crosspaste.cli.commands.HistoryCommand
import com.crosspaste.cli.commands.PasteCommand
import com.crosspaste.cli.commands.SearchCommand
import com.crosspaste.cli.commands.StatusCommand
import com.crosspaste.cli.commands.TagsCommand
import com.crosspaste.cli.commands.VersionCommand
import com.github.ajalt.clikt.completion.completionOption
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.core.subcommands
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

class CrossPasteCommand : CliktCommand(name = cliCommandName) {

    override fun help(context: Context): String = "CrossPaste CLI - interact with your local CrossPaste application"

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
        currentContext.obj = CliContext(json = json, autoStart = autoStart)
    }

    init {
        completionOption()
        subcommands(
            StatusCommand(),
            PasteCommand(),
            HistoryCommand(),
            SearchCommand(),
            CopyCommand(),
            DeleteCommand(),
            DevicesCommand(),
            ConfigCommand(),
            TagsCommand(),
            VersionCommand(),
        )
    }
}

data class CliContext(
    val json: Boolean = false,
    /** Tri-state launch consent: true = always start, false = never, null = ask. */
    val autoStart: Boolean? = null,
)
