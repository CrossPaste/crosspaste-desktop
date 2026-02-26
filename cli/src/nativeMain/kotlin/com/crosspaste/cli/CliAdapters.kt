package com.crosspaste.cli

import com.crosspaste.cli.platform.NativePlatformPathProvider
import com.crosspaste.config.AppConfig
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.path.PlatformUserDataPathProvider
import com.crosspaste.utils.DeviceUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

/**
 * Read-only AppConfig for CLI, deserialized from the same appConfig.json the desktop app uses.
 * Fields not present in the JSON will use the defaults below.
 */
@Serializable
data class CliReadOnlyAppConfig(
    override val appInstanceId: String = "",
    override val language: String = "en",
    override val font: String = "",
    override val isFollowSystemTheme: Boolean = true,
    override val isDarkTheme: Boolean = false,
    override val port: Int = 13129,
    override val enableEncryptSync: Boolean = false,
    override val enableExpirationCleanup: Boolean = true,
    override val imageCleanTimeIndex: Int = 6,
    override val fileCleanTimeIndex: Int = 6,
    override val enableThresholdCleanup: Boolean = true,
    override val maxStorage: Long = 2048,
    override val cleanupPercentage: Int = 20,
    override val enableDiscovery: Boolean = true,
    override val blacklist: String = "[]",
    override val enableSkipPreLaunchPasteboardContent: Boolean = true,
    override val lastPasteboardChangeCount: Int = -1,
    override val enablePasteboardListening: Boolean = true,
    override val maxBackupFileSize: Long = 32,
    override val enabledSyncFileSizeLimit: Boolean = true,
    override val maxSyncFileSize: Long = 512,
    override val useDefaultStoragePath: Boolean = true,
    override val storagePath: String = "",
    override val enableSoundEffect: Boolean = true,
    override val pastePrimaryTypeOnly: Boolean = true,
    override val useNetworkInterfaces: String = "[]",
    override val enableSyncText: Boolean = true,
    override val enableSyncUrl: Boolean = true,
    override val enableSyncHtml: Boolean = true,
    override val enableSyncRtf: Boolean = true,
    override val enableSyncImage: Boolean = true,
    override val enableSyncFile: Boolean = true,
    override val enableSyncColor: Boolean = true,
) : AppConfig {
    override fun copy(
        key: String,
        value: Any,
    ): AppConfig = this
}

/**
 * Read-only ConfigManager for CLI. Loads config from the desktop app's appConfig.json.
 */
class CliConfigManager(
    nativePathProvider: NativePlatformPathProvider,
) : CommonConfigManager {

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val appConfig: AppConfig =
        run {
            val configPath = nativePathProvider.getDefaultUserDataPath().resolve("appConfig.json")
            try {
                val content = FileSystem.SYSTEM.read(configPath) { readUtf8() }
                json.decodeFromString<CliReadOnlyAppConfig>(content)
            } catch (_: Exception) {
                CliReadOnlyAppConfig()
            }
        }

    override val deviceUtils: DeviceUtils =
        object : DeviceUtils {
            override fun createAppInstanceId(): String = appConfig.appInstanceId

            override fun getDeviceId(): String = ""

            override fun getDeviceName(): String = "CLI"
        }

    override val config: StateFlow<AppConfig> = MutableStateFlow(appConfig)

    override fun loadConfig(): AppConfig = appConfig

    override fun updateConfig(
        key: String,
        value: Any,
    ) {
        // CLI is read-only
    }

    override fun updateConfig(
        keys: List<String>,
        values: List<Any>,
    ) {
        // CLI is read-only
    }
}

/**
 * Adapter to expose NativePlatformPathProvider as PlatformUserDataPathProvider.
 */
class CliPlatformUserDataPathProvider(
    private val nativeProvider: NativePlatformPathProvider,
) : PlatformUserDataPathProvider {
    override fun getUserDefaultStoragePath(): Path = nativeProvider.getDefaultUserDataPath()
}
