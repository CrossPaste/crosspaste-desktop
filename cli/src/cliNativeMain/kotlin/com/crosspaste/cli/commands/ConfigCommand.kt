package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.crosspaste.config.AppConfig
import com.crosspaste.config.CommonConfigManager
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class ConfigEntryDto(
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
        runWithDao {
            val configManager = getDao<CommonConfigManager>()
            val entries = configManager.getCurrentConfig().toEntries()

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
        runWithDao {
            val configManager = getDao<CommonConfigManager>()
            val parsed = parseConfigValue(value)
            configManager.updateConfig(key, parsed)
            echo("Config '$key' set to '$value'.")
        }
}

@Suppress("MagicNumber")
fun AppConfig.toEntries(): List<ConfigEntryDto> =
    listOf(
        ConfigEntryDto("port", port.toString()),
        ConfigEntryDto("language", language),
        ConfigEntryDto("enablePasteboardListening", enablePasteboardListening.toString()),
        ConfigEntryDto("enableEncryptSync", enableEncryptSync.toString()),
        ConfigEntryDto("enableExpirationCleanup", enableExpirationCleanup.toString()),
        ConfigEntryDto("imageCleanTimeIndex", imageCleanTimeIndex.toString()),
        ConfigEntryDto("fileCleanTimeIndex", fileCleanTimeIndex.toString()),
        ConfigEntryDto("enableThresholdCleanup", enableThresholdCleanup.toString()),
        ConfigEntryDto("maxStorage", maxStorage.toString()),
        ConfigEntryDto("cleanupPercentage", cleanupPercentage.toString()),
        ConfigEntryDto("enableDiscovery", enableDiscovery.toString()),
        ConfigEntryDto("enableSkipPreLaunchPasteboardContent", enableSkipPreLaunchPasteboardContent.toString()),
        ConfigEntryDto("enableSoundEffect", enableSoundEffect.toString()),
        ConfigEntryDto("pastePrimaryTypeOnly", pastePrimaryTypeOnly.toString()),
        ConfigEntryDto("enabledSyncFileSizeLimit", enabledSyncFileSizeLimit.toString()),
        ConfigEntryDto("maxSyncFileSize", maxSyncFileSize.toString()),
        ConfigEntryDto("maxBackupFileSize", maxBackupFileSize.toString()),
        ConfigEntryDto("useDefaultStoragePath", useDefaultStoragePath.toString()),
        ConfigEntryDto("storagePath", storagePath),
        ConfigEntryDto("enableSyncText", enableSyncText.toString()),
        ConfigEntryDto("enableSyncUrl", enableSyncUrl.toString()),
        ConfigEntryDto("enableSyncHtml", enableSyncHtml.toString()),
        ConfigEntryDto("enableSyncRtf", enableSyncRtf.toString()),
        ConfigEntryDto("enableSyncImage", enableSyncImage.toString()),
        ConfigEntryDto("enableSyncFile", enableSyncFile.toString()),
        ConfigEntryDto("enableSyncColor", enableSyncColor.toString()),
    )

private fun parseConfigValue(value: String): Any =
    when {
        value.equals("true", ignoreCase = true) -> true
        value.equals("false", ignoreCase = true) -> false
        value.toLongOrNull() != null -> value.toLong()
        else -> value
    }
