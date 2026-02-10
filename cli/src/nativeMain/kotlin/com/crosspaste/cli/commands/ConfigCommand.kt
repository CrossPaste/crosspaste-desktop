package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import io.ktor.client.call.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class ConfigEntryDto(
    val key: String,
    val value: String,
)

@Serializable
data class ConfigUpdateRequest(
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
        runWithClient { client ->
            val response = client.get("/cli/config")
            handleResponse(response) { resp ->
                val entries = resp.body<List<ConfigEntryDto>>()
                if (ctx.json) {
                    echo(
                        cliJson.encodeToString(
                            ListSerializer(ConfigEntryDto.serializer()),
                            entries,
                        ),
                    )
                } else {
                    printConfig(entries)
                }
            }
        }
    }

    private fun printConfig(entries: List<ConfigEntryDto>) {
        echo("Configuration:")
        echo("")
        val maxKeyLen = entries.maxOfOrNull { it.key.length } ?: 0
        for (entry in entries) {
            echo("  ${entry.key.padEnd(maxKeyLen)}  ${entry.value}")
        }
    }
}

class ConfigSetCommand : CliktCommand(name = "set") {

    override fun help(context: Context): String = "Set a configuration value"

    private val key by argument(help = "Configuration key")

    private val value by argument(help = "New value")

    override fun run() =
        runWithClient { client ->
            val body =
                Json.encodeToString(
                    ConfigUpdateRequest.serializer(),
                    ConfigUpdateRequest(key = key, value = value),
                )
            val response = client.put("/cli/config", body)
            handleResponse(response) {
                echo("Config '$key' set to '$value'.")
            }
        }
}
