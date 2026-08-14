package com.crosspaste.cli.platform

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * Minimal projection of the app's appConfig.json: the CLI only needs the
 * storage location to find the cli-endpoint.json discovery file. This must
 * mirror the app's UserDataPathProvider.getUserDataPath() resolution.
 */
@Serializable
data class CliAppConfig(
    val useDefaultStoragePath: Boolean = true,
    val storagePath: String = "",
)

class CliConfigReader(
    private val platformPathProvider: NativePlatformPathProvider,
) {

    companion object {
        const val CLI_ENDPOINT_FILE_NAME = "cli-endpoint.json"
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    fun readConfig(): CliAppConfig {
        val configPath = platformPathProvider.getDefaultUserDataPath().resolve("appConfig.json")
        return try {
            val content = FileSystem.SYSTEM.read(configPath) { readUtf8() }
            json.decodeFromString<CliAppConfig>(content)
        } catch (_: Exception) {
            CliAppConfig()
        }
    }

    fun resolveUserDataPath(): Path {
        val config = readConfig()
        return if (config.useDefaultStoragePath || config.storagePath.isEmpty()) {
            platformPathProvider.getDefaultUserDataPath()
        } else {
            config.storagePath.toPath(normalize = true)
        }
    }

    fun resolveEndpointFilePath(): Path = resolveUserDataPath().resolve(CLI_ENDPOINT_FILE_NAME)
}
