package com.crosspaste.config

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val appInstanceId: String,
    val language: String,
    val enableAutoStartUp: Boolean = true,
    val enableDebugMode: Boolean = false,
    val isFollowSystemTheme: Boolean = true,
    val isDarkTheme: Boolean = false,
    val port: Int = 13129,
    val enableEncryptSync: Boolean = false,
    val enableExpirationCleanup: Boolean = true,
    val imageCleanTimeIndex: Int = 6,
    val fileCleanTimeIndex: Int = 6,
    val enableThresholdCleanup: Boolean = true,
    // MB
    val maxStorage: Long = 2048,
    val cleanupPercentage: Int = 20,
    val enableDiscovery: Boolean = true,
    val blacklist: String = "[]",
    val enableSkipPriorPasteboardContent: Boolean = true,
    val lastPasteboardChangeCount: Int = -1,
    val enablePasteboardListening: Boolean = true,
    val showTutorial: Boolean = true,
    // MB
    val maxBackupFileSize: Long = 32,
    val enabledSyncFileSizeLimit: Boolean = true,
    val maxSyncFileSize: Long = 512,
    val useDefaultStoragePath: Boolean = true,
    val storagePath: String = "",
) {
    fun copy(
        key: String,
        value: Any,
    ): AppConfig {
        return this.copy(
            appInstanceId = appInstanceId,
            language = if (key == "language") value as String else language,
            enableAutoStartUp = if (key == "enableAutoStartUp") value as Boolean else enableAutoStartUp,
            enableDebugMode = if (key == "enableDebugMode") value as Boolean else enableDebugMode,
            isFollowSystemTheme = if (key == "isFollowSystemTheme") value as Boolean else isFollowSystemTheme,
            isDarkTheme = if (key == "isDarkTheme") value as Boolean else isDarkTheme,
            port = if (key == "port") value as Int else port,
            enableEncryptSync = if (key == "enableEncryptSync") value as Boolean else enableEncryptSync,
            enableExpirationCleanup = if (key == "enableExpirationCleanup") value as Boolean else enableExpirationCleanup,
            imageCleanTimeIndex = if (key == "imageCleanTimeIndex") value as Int else imageCleanTimeIndex,
            fileCleanTimeIndex = if (key == "fileCleanTimeIndex") value as Int else fileCleanTimeIndex,
            enableThresholdCleanup = if (key == "enableThresholdCleanup") value as Boolean else enableThresholdCleanup,
            maxStorage = if (key == "maxStorage") value as Long else maxStorage,
            cleanupPercentage = if (key == "cleanupPercentage") value as Int else cleanupPercentage,
            enableDiscovery = if (key == "enableDiscovery") value as Boolean else enableDiscovery,
            blacklist = if (key == "blacklist") value as String else blacklist,
            enableSkipPriorPasteboardContent =
                if (key == "enableSkipPriorPasteboardContent") {
                    value as Boolean
                } else {
                    enableSkipPriorPasteboardContent
                },
            lastPasteboardChangeCount = if (key == "lastPasteboardChangeCount") value as Int else lastPasteboardChangeCount,
            enablePasteboardListening = if (key == "enablePasteboardListening") value as Boolean else enablePasteboardListening,
            showTutorial = if (key == "showTutorial") value as Boolean else showTutorial,
            maxBackupFileSize = if (key == "maxBackupFileSize") value as Long else maxBackupFileSize,
            enabledSyncFileSizeLimit = if (key == "enabledSyncFileSizeLimit") value as Boolean else enabledSyncFileSizeLimit,
            maxSyncFileSize = if (key == "maxSyncFileSize") value as Long else maxSyncFileSize,
            useDefaultStoragePath = if (key == "useDefaultStoragePath") value as Boolean else useDefaultStoragePath,
            storagePath = if (key == "storagePath") value as String else storagePath,
        )
    }
}
