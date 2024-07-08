package com.crosspaste.config

import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class AppConfig(
    val appInstanceId: String,
    val language: String = Locale.getDefault().language,
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
)
