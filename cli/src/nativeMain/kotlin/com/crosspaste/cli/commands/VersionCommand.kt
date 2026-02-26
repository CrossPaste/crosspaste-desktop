package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import kotlinx.serialization.Serializable

@Serializable
data class VersionInfo(
    val cliVersion: String,
)

class VersionCommand : CliktCommand(name = "version") {

    override fun help(context: Context): String = "Show CLI version"

    private val ctx by requireObject<CliContext>()

    override fun run() {
        val info = VersionInfo(cliVersion = CLI_VERSION)
        if (ctx.json) {
            echo(cliJson.encodeToString(VersionInfo.serializer(), info))
        } else {
            echo("CLI version:  ${info.cliVersion}")
        }
    }
}
