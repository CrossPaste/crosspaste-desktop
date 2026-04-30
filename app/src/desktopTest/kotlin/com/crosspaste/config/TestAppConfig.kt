package com.crosspaste.config

import com.crosspaste.config.AppConfig.Companion.toBoolean

data class TestAppConfig(
    override val language: String = "en",
    override val font: String = "",
    override val isFollowSystemTheme: Boolean = true,
    override val isDarkTheme: Boolean = false,
    override val port: Int = 0,
    override val enableEncryptSync: Boolean = false,
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
    override val enablePasteboardListening: Boolean = true,
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
    ): TestAppConfig =
        when (key) {
            "enableRemoteShowPairingCode" -> copy(enableRemoteShowPairingCode = toBoolean(value))
            else -> this
        }
}
