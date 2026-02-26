package com.crosspaste.cli

import com.crosspaste.cli.commands.ConfigCommand
import com.crosspaste.cli.commands.CopyCommand
import com.crosspaste.cli.commands.DeleteCommand
import com.crosspaste.cli.commands.DevicesCommand
import com.crosspaste.cli.commands.FavCommand
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

class CrossPasteCommand : CliktCommand(name = "crosspaste") {

    override fun help(context: Context): String = "CrossPaste CLI - interact with your local CrossPaste application"

    val json by option("--json", help = "Output in JSON format for machine consumption").flag()

    override fun run() {
        currentContext.obj = CliContext(json = json)
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
            FavCommand(),
            DevicesCommand(),
            ConfigCommand(),
            TagsCommand(),
            VersionCommand(),
        )
    }
}

data class CliContext(
    val json: Boolean = false,
)
