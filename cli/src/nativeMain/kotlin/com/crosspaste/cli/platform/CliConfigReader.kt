package com.crosspaste.cli.platform

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

@Serializable
data class CliAppConfig(
    val port: Int = 13129,
    val useDefaultStoragePath: Boolean = true,
    val storagePath: String = "",
)

class CliConfigReader(
    private val platformPathProvider: NativePlatformPathProvider,
) {

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

    fun resolveTokenPath(): Path = resolveUserDataPath().resolve("cli-token")

    fun resolvePort(): Int = readConfig().port
}
