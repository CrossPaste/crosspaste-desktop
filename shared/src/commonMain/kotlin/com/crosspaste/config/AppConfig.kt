package com.crosspaste.config

interface AppConfig {
    val appInstanceId: String
    val language: String
    val font: String
    val isFollowSystemTheme: Boolean
    val isDarkTheme: Boolean
    val port: Int
    val enableEncryptSync: Boolean
    val enableExpirationCleanup: Boolean
    val imageCleanTimeIndex: Int
    val fileCleanTimeIndex: Int
    val enableThresholdCleanup: Boolean

    // MB
    val maxStorage: Long
    val cleanupPercentage: Int
    val enableDiscovery: Boolean
    val blacklist: String
    val enableSkipPreLaunchPasteboardContent: Boolean
    val lastPasteboardChangeCount: Int
    val enablePasteboardListening: Boolean
    val sourceExclusions: String

    // MB
    val maxBackupFileSize: Long
    val enabledSyncFileSizeLimit: Boolean
    val maxSyncFileSize: Long
    val useDefaultStoragePath: Boolean
    val storagePath: String
    val enableSoundEffect: Boolean
    val pastePrimaryTypeOnly: Boolean
    val useNetworkInterfaces: String

    // Sync content type controls
    val enableSyncText: Boolean
    val enableSyncUrl: Boolean
    val enableSyncHtml: Boolean
    val enableSyncRtf: Boolean
    val enableSyncImage: Boolean
    val enableSyncFile: Boolean
    val enableSyncColor: Boolean

    fun copy(
        key: String,
        value: Any,
    ): AppConfig

    companion object {
        // For compatibility with different storage implementations
        // it is convenient to read the correct configuration from the storage
        fun toBoolean(any: Any): Boolean =
            when (any) {
                is Boolean -> any
                is String -> any.toBoolean()
                is Int -> any != 0
                is Long -> any != 0L
                else -> false
            }

        fun toInt(any: Any): Int =
            when (any) {
                is Int -> any
                is String -> any.toIntOrNull() ?: 0
                is Boolean -> if (any) 1 else 0
                is Long -> any.toInt()
                else -> 0
            }

        fun toLong(any: Any): Long =
            when (any) {
                is Long -> any
                is String -> any.toLongOrNull() ?: 0L
                is Boolean -> if (any) 1L else 0L
                is Int -> any.toLong()
                else -> 0L
            }

        fun toString(any: Any): String = any.toString()
    }
}
