package com.crosspaste.config

import com.crosspaste.utils.getLocaleUtils
import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val appInstanceId: String,
    val language: String = getLocaleUtils().getCurrentLocale(),
    val enableAutoStartUp: Boolean = true,
    val isFollowSystemTheme: Boolean = true,
    val isDarkTheme: Boolean = false,
    val port: Int = 13129,
    val isEncryptSync: Boolean = false,
    val isExpirationCleanup: Boolean = true,
    val imageCleanTimeIndex: Int = 6,
    val fileCleanTimeIndex: Int = 6,
    val isThresholdCleanup: Boolean = true,
    // MB
    val maxStorage: Long = 2048,
    val cleanupPercentage: Int = 20,
    val isAllowDiscovery: Boolean = true,
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
            isFollowSystemTheme = if (key == "isFollowSystemTheme") value as Boolean else isFollowSystemTheme,
            isDarkTheme = if (key == "isDarkTheme") value as Boolean else isDarkTheme,
            port = if (key == "port") value as Int else port,
            isEncryptSync = if (key == "isEncryptSync") value as Boolean else isEncryptSync,
            isExpirationCleanup = if (key == "isExpirationCleanup") value as Boolean else isExpirationCleanup,
            imageCleanTimeIndex = if (key == "imageCleanTimeIndex") value as Int else imageCleanTimeIndex,
            fileCleanTimeIndex = if (key == "fileCleanTimeIndex") value as Int else fileCleanTimeIndex,
            isThresholdCleanup = if (key == "isThresholdCleanup") value as Boolean else isThresholdCleanup,
            maxStorage = if (key == "maxStorage") value as Long else maxStorage,
            cleanupPercentage = if (key == "cleanupPercentage") value as Int else cleanupPercentage,
            isAllowDiscovery = if (key == "isAllowDiscovery") value as Boolean else isAllowDiscovery,
            blacklist = if (key == "blacklist") value as String else blacklist,
            enableSkipPriorPasteboardContent = if (key == "enableSkipPriorPasteboardContent") value as Boolean else enableSkipPriorPasteboardContent,
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
