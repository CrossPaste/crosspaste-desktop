package com.crosspaste.cli

import com.crosspaste.cli.commands.EXIT_CODE_USAGE
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.parse
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val command = CrossPasteCommand()
    try {
        command.parse(args)
    } catch (e: CliktError) {
        command.echoFormattedHelp(e)
        // Clikt's default status code for a usage error is 1, but the CLI's
        // documented contract reserves 2 for usage errors (1 = generic error,
        // 3 = app not running). Remap unless a specific code was set.
        val statusCode = if (e is UsageError && e.statusCode == 1) EXIT_CODE_USAGE else e.statusCode
        exitProcess(statusCode)
    }
}
