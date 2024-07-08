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
    val maxStorage: Long = 2048,
    val cleanupPercentage: Int = 20,
    val isAllowDiscovery: Boolean = true,
    val blacklist: String = "[]",
    val lastPasteboardChangeCount: Int = -1,
    val enablePasteboardListening: Boolean = true,
) {

    fun copy(
        key: String,
        value: Any,
    ): AppConfig {
        return when (key) {
            "language" -> AppConfig(appInstanceId, language = value as String)
            "enableAutoStartUp" -> AppConfig(appInstanceId, enableAutoStartUp = value as Boolean)
            "isFollowSystemTheme" -> AppConfig(appInstanceId, isFollowSystemTheme = value as Boolean)
            "isDarkTheme" -> AppConfig(appInstanceId, isDarkTheme = value as Boolean)
            "port" -> AppConfig(appInstanceId, port = value as Int)
            "isEncryptSync" -> AppConfig(appInstanceId, isEncryptSync = value as Boolean)
            "isExpirationCleanup" -> AppConfig(appInstanceId, isExpirationCleanup = value as Boolean)
            "imageCleanTimeIndex" -> AppConfig(appInstanceId, imageCleanTimeIndex = value as Int)
            "fileCleanTimeIndex" -> AppConfig(appInstanceId, fileCleanTimeIndex = value as Int)
            "isThresholdCleanup" -> AppConfig(appInstanceId, isThresholdCleanup = value as Boolean)
            "maxStorage" -> AppConfig(appInstanceId, maxStorage = value as Long)
            "cleanupPercentage" -> AppConfig(appInstanceId, cleanupPercentage = value as Int)
            "isAllowDiscovery" -> AppConfig(appInstanceId, isAllowDiscovery = value as Boolean)
            "blacklist" -> AppConfig(appInstanceId, blacklist = value as String)
            "lastPasteboardChangeCount" -> AppConfig(appInstanceId, lastPasteboardChangeCount = value as Int)
            "enablePasteboardListening" -> AppConfig(appInstanceId, enablePasteboardListening = value as Boolean)
            else -> this
        }
    }
}
