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

        /**
         * Secondary discovery pointer a dev instance (`./gradlew app:run`)
         * writes into the installed app's default user-data directory; its
         * real endpoint file lives in the repo's dev user dir, which this
         * CLI cannot know. Same record format as the primary file.
         */
        const val CLI_DEV_ENDPOINT_FILE_NAME = "cli-endpoint.dev.json"
    }

    /**
     * When true the CLI targets the dev pointer instead of the installed
     * app's endpoint file. Set only by AppReadinessChecker after verifying a
     * live dev instance while no production instance answers; never set on a
     * release user's machine, where the dev pointer file does not exist.
     */
    var devEndpointActive: Boolean = false

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

    fun resolveEndpointFilePath(): Path =
        if (devEndpointActive) {
            devEndpointFilePath()
        } else {
            primaryEndpointFilePath()
        }

    fun primaryEndpointFilePath(): Path = resolveUserDataPath().resolve(CLI_ENDPOINT_FILE_NAME)

    fun devEndpointFilePath(): Path = platformPathProvider.getDefaultUserDataPath().resolve(CLI_DEV_ENDPOINT_FILE_NAME)
}
