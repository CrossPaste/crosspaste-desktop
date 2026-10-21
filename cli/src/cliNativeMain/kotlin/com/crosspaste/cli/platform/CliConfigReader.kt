@file:OptIn(ExperimentalSerializationApi::class)

package com.crosspaste.cli.platform

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * Minimal projection of the app's settings: the CLI only needs the storage
 * location to find the cli-endpoint.json discovery file. This must mirror the
 * app's UserDataPathProvider.getUserDataPath() resolution.
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

        /**
         * The app's settings, a Jetpack DataStore preferences file (see the
         * app's ConfigScope.SETTINGS). Written atomically by the app, so a read
         * never observes a partial file.
         */
        const val SETTINGS_FILE_NAME = "appConfig.preferences_pb"

        /** The single-document config of app versions before DataStore. */
        const val LEGACY_CONFIG_FILE_NAME = "appConfig.json"

        private const val USE_DEFAULT_STORAGE_PATH_KEY = "useDefaultStoragePath"

        private const val STORAGE_PATH_KEY = "storagePath"
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

    /**
     * Reads the settings file, falling back to the legacy JSON document while
     * an older app version is installed; defaults when neither can be read.
     */
    fun readConfig(): CliAppConfig {
        val userDataPath = platformPathProvider.getDefaultUserDataPath()
        return readPreferences(userDataPath.resolve(SETTINGS_FILE_NAME))
            ?: readLegacyJson(userDataPath.resolve(LEGACY_CONFIG_FILE_NAME))
            ?: CliAppConfig()
    }

    private fun readPreferences(path: Path): CliAppConfig? =
        try {
            val preferences = decodePreferences(FileSystem.SYSTEM.read(path) { readByteArray() })
            CliAppConfig(
                useDefaultStoragePath = preferences[USE_DEFAULT_STORAGE_PATH_KEY]?.boolean ?: true,
                storagePath = preferences[STORAGE_PATH_KEY]?.string ?: "",
            )
        } catch (_: Exception) {
            null
        }

    private fun readLegacyJson(path: Path): CliAppConfig? =
        try {
            json.decodeFromString<CliAppConfig>(FileSystem.SYSTEM.read(path) { readUtf8() })
        } catch (_: Exception) {
            null
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

/**
 * Decodes a DataStore preferences file into its entries by key name.
 *
 * The file is the `PreferenceMap` message of androidx.datastore's
 * preferences.proto; the classes below mirror it field for field, exactly as
 * DataStore's own non-JVM serializer does, so no DataStore dependency is needed.
 */
internal fun decodePreferences(bytes: ByteArray): Map<String, PreferenceValue> =
    ProtoBuf.decodeFromByteArray(PreferenceMap.serializer(), bytes).preferences

@Serializable
internal data class PreferenceMap(
    @ProtoNumber(1) val preferences: Map<String, PreferenceValue> = emptyMap(),
)

@Serializable
internal data class PreferenceValue(
    @ProtoNumber(1) val boolean: Boolean? = null,
    @ProtoNumber(2) val float: Float? = null,
    @ProtoNumber(3) val integer: Int? = null,
    @ProtoNumber(4) val long: Long? = null,
    @ProtoNumber(5) val string: String? = null,
    @ProtoNumber(6) val stringSet: PreferenceStringSet? = null,
    @ProtoNumber(7) val double: Double? = null,
    @ProtoNumber(8) val bytes: ByteArray? = null,
)

@Serializable
internal data class PreferenceStringSet(
    @ProtoNumber(1) val strings: List<String> = emptyList(),
)
