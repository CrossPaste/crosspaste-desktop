package com.crosspaste.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.fputs
import platform.posix.pclose
import platform.posix.popen
import kotlin.experimental.ExperimentalNativeApi

class CopyCommand : CliktCommand(name = "copy") {

    override fun help(context: Context): String = "Copy text to the system clipboard"

    private val text by argument(help = "Text to copy to clipboard")

    override fun run() {
        try {
            copyToSystemClipboard(text)
            echo("Copied to clipboard.")
        } catch (e: Exception) {
            echo("Error: ${e.message}", err = true)
            throw ProgramResult(1)
        }
    }
}

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
private fun copyToSystemClipboard(text: String) {
    val command =
        when (Platform.osFamily) {
            OsFamily.MACOSX -> "pbcopy"
            OsFamily.LINUX -> "xclip -selection clipboard"
            OsFamily.WINDOWS -> "clip.exe"
            else -> error("Unsupported platform for clipboard operations")
        }
    val pipe = popen(command, "w") ?: error("Failed to run '$command'. Is it installed?")
    fputs(text, pipe)
    val exitCode = pclose(pipe)
    if (exitCode != 0) {
        error("Clipboard command '$command' failed (exit $exitCode)")
    }
}
