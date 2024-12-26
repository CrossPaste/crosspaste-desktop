package com.crosspaste.config

import com.crosspaste.ui.theme.GrassColor
import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val appInstanceId: String,
    val language: String,
    val enableAutoStartUp: Boolean = true,
    val enableDebugMode: Boolean = false,
    val isFollowSystemTheme: Boolean = true,
    val isDarkTheme: Boolean = false,
    val themeColor: String = GrassColor.name,
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
    val enableSoundEffect: Boolean = true,
) {
    fun copy(
        key: String,
        value: Any,
    ): AppConfig {
        return this.copy(
            appInstanceId = appInstanceId,
            language = if (key == "language") toString(value) else language,
            enableAutoStartUp = if (key == "enableAutoStartUp") toBoolean(value) else enableAutoStartUp,
            enableDebugMode = if (key == "enableDebugMode") toBoolean(value) else enableDebugMode,
            isFollowSystemTheme = if (key == "isFollowSystemTheme") toBoolean(value) else isFollowSystemTheme,
            isDarkTheme = if (key == "isDarkTheme") toBoolean(value) else isDarkTheme,
            themeColor = if (key == "themeColor") toString(value) else themeColor,
            port = if (key == "port") toInt(value) else port,
            enableEncryptSync = if (key == "enableEncryptSync") toBoolean(value) else enableEncryptSync,
            enableExpirationCleanup = if (key == "enableExpirationCleanup") toBoolean(value) else enableExpirationCleanup,
            imageCleanTimeIndex = if (key == "imageCleanTimeIndex") toInt(value) else imageCleanTimeIndex,
            fileCleanTimeIndex = if (key == "fileCleanTimeIndex") toInt(value) else fileCleanTimeIndex,
            enableThresholdCleanup = if (key == "enableThresholdCleanup") toBoolean(value) else enableThresholdCleanup,
            maxStorage = if (key == "maxStorage") toLong(value) else maxStorage,
            cleanupPercentage = if (key == "cleanupPercentage") toInt(value) else cleanupPercentage,
            enableDiscovery = if (key == "enableDiscovery") toBoolean(value) else enableDiscovery,
            blacklist = if (key == "blacklist") toString(value) else blacklist,
            enableSkipPriorPasteboardContent =
                if (key == "enableSkipPriorPasteboardContent") {
                    toBoolean(value)
                } else {
                    enableSkipPriorPasteboardContent
                },
            lastPasteboardChangeCount = if (key == "lastPasteboardChangeCount") toInt(value) else lastPasteboardChangeCount,
            enablePasteboardListening = if (key == "enablePasteboardListening") toBoolean(value) else enablePasteboardListening,
            showTutorial = if (key == "showTutorial") toBoolean(value) else showTutorial,
            maxBackupFileSize = if (key == "maxBackupFileSize") toLong(value) else maxBackupFileSize,
            enabledSyncFileSizeLimit = if (key == "enabledSyncFileSizeLimit") toBoolean(value) else enabledSyncFileSizeLimit,
            maxSyncFileSize = if (key == "maxSyncFileSize") toLong(value) else maxSyncFileSize,
            useDefaultStoragePath = if (key == "useDefaultStoragePath") toBoolean(value) else useDefaultStoragePath,
            storagePath = if (key == "storagePath") toString(value) else storagePath,
            enableSoundEffect = if (key == "enableSoundEffect") toBoolean(value) else enableSoundEffect,
        )
    }

    // For compatibility with different storage implementations
    // it is convenient to read the correct configuration from the storage
    private fun toBoolean(any: Any): Boolean {
        return when (any) {
            is Boolean -> any
            is String -> any.toBoolean()
            is Int -> any != 0
            is Long -> any != 0L
            else -> false
        }
    }

    private fun toInt(any: Any): Int {
        return when (any) {
            is Int -> any
            is String -> any.toIntOrNull() ?: 0
            is Boolean -> if (any) 1 else 0
            is Long -> any.toInt()
            else -> 0
        }
    }

    private fun toLong(any: Any): Long {
        return when (any) {
            is Long -> any
            is String -> any.toLongOrNull() ?: 0L
            is Boolean -> if (any) 1L else 0L
            is Int -> any.toLong()
            else -> 0L
        }
    }

    private fun toString(any: Any): String {
        return any.toString()
    }
}
