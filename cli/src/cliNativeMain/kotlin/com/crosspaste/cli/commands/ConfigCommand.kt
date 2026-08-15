package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class ConfigSetRequest(
    val key: String,
    val value: String,
)

class ConfigCommand : CliktCommand(name = "config") {

    override fun help(context: Context): String = "View or update configuration"

    override val invokeWithoutSubcommand = true

    private val ctx by requireObject<CliContext>()

    init {
        subcommands(ConfigSetCommand())
    }

    override fun run() {
        if (currentContext.invokedSubcommand != null) return
        runCli { client ->
            val config = client.getBody("/cli/config", JsonObject.serializer())

            if (ctx.json) {
                echo(cliJson.encodeToString(JsonObject.serializer(), config))
            } else {
                printConfig(config)
            }
        }
    }

    private fun printConfig(config: JsonObject) {
        echo("Configuration:")
        echo("")
        val entries = config.entries.sortedBy { it.key }
        val maxKeyLen = entries.maxOfOrNull { it.key.length } ?: 0
        for ((key, value) in entries) {
            val rendered = if (value is JsonPrimitive) value.content else value.toString()
            echo("  ${key.padEnd(maxKeyLen)}  $rendered")
        }
    }
}

class ConfigSetCommand : CliktCommand(name = "set") {

    override fun help(context: Context): String = "Set a configuration value"

    private val ctx by requireObject<CliContext>()

    private val key by argument(help = "Configuration key")

    private val value by argument(help = "New value")

    override fun run() =
        runCli { client ->
            val body =
                cliJson.encodeToString(
                    ConfigSetRequest.serializer(),
                    ConfigSetRequest(key, value),
                )
            val response = client.putBody("/cli/config", body, MessageResponse.serializer())
            echoMessage(ctx, response)
        }
}
