package com.clipevery.config

import com.clipevery.app.AppEnv
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.Locale

@Serializable
data class AppConfig(
    @Transient val appEnv: AppEnv = AppEnv.PRODUCTION,
    val appInstanceId: String,
    val language: String = Locale.getDefault().language,
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
    val lastClipboardChangeCount: Int = -1,
    val enableClipboardListening: Boolean = true,
) {

    constructor(other: AppConfig, appEnv: AppEnv) : this(
        appEnv = appEnv,
        appInstanceId = other.appInstanceId,
        language = other.language,
        isFollowSystemTheme = other.isFollowSystemTheme,
        isDarkTheme = other.isDarkTheme,
        port = other.port,
        isEncryptSync = other.isEncryptSync,
        isExpirationCleanup = other.isExpirationCleanup,
        imageCleanTimeIndex = other.imageCleanTimeIndex,
        fileCleanTimeIndex = other.fileCleanTimeIndex,
        isThresholdCleanup = other.isThresholdCleanup,
        maxStorage = other.maxStorage,
        cleanupPercentage = other.cleanupPercentage,
        isAllowDiscovery = other.isAllowDiscovery,
        blacklist = other.blacklist,
        lastClipboardChangeCount = other.lastClipboardChangeCount,
        enableClipboardListening = other.enableClipboardListening,
    )
}
