package com.crosspaste.e2e.peer

import com.crosspaste.config.AppConfig
import com.crosspaste.config.AppConfig.Companion.toBoolean
import com.crosspaste.config.CommonConfigManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Minimal [AppConfig] for the headless peer. All sync-content flags default to true and
 * encrypted sync is on so we exercise the production-equivalent paths.
 */
data class E2eAppConfig(
    override val language: String = "en",
    override val font: String = "",
    override val isFollowSystemTheme: Boolean = true,
    override val isDarkTheme: Boolean = false,
    override val port: Int = 0,
    override val enableEncryptSync: Boolean = true,
    override val enableExpirationCleanup: Boolean = false,
    override val imageCleanTimeIndex: Int = 6,
    override val fileCleanTimeIndex: Int = 6,
    override val enableThresholdCleanup: Boolean = false,
    override val maxStorage: Long = 2048,
    override val cleanupPercentage: Int = 20,
    override val enableDiscovery: Boolean = true,
    override val blacklist: String = "[]",
    override val enableSkipPreLaunchPasteboardContent: Boolean = true,
    override val lastPasteboardChangeCount: Int = -1,
    override val enablePasteboardListening: Boolean = false,
    override val maxBackupFileSize: Long = 32,
    override val enabledSyncFileSizeLimit: Boolean = true,
    override val maxSyncFileSize: Long = 512,
    override val useDefaultStoragePath: Boolean = true,
    override val storagePath: String = "",
    override val enableSoundEffect: Boolean = false,
    override val pastePrimaryTypeOnly: Boolean = true,
    override val useNetworkInterfaces: String = "[]",
    override val enableSyncText: Boolean = true,
    override val enableSyncUrl: Boolean = true,
    override val enableSyncHtml: Boolean = true,
    override val enableSyncRtf: Boolean = true,
    override val enableSyncImage: Boolean = true,
    override val enableSyncFile: Boolean = true,
    override val enableSyncColor: Boolean = true,
    override val enableRemoteShowPairingCode: Boolean = true,
) : AppConfig {
    override fun copy(
        key: String,
        value: Any,
    ): E2eAppConfig =
        when (key) {
            "enableEncryptSync" -> copy(enableEncryptSync = toBoolean(value))
            "enableRemoteShowPairingCode" -> copy(enableRemoteShowPairingCode = toBoolean(value))
            else -> this
        }
}

class E2eConfigManager(
    initial: E2eAppConfig = E2eAppConfig(),
) : CommonConfigManager {
    private val _config = MutableStateFlow<AppConfig>(initial)

    override val config: StateFlow<AppConfig> = _config

    override fun loadConfig(): AppConfig = _config.value

    override fun updateConfig(
        key: String,
        value: Any,
    ) {
        _config.value = _config.value.copy(key, value)
    }

    override fun updateConfig(
        keys: List<String>,
        values: List<Any>,
    ) {
        var current = _config.value
        for (i in keys.indices) {
            current = current.copy(keys[i], values[i])
        }
        _config.value = current
    }
}
