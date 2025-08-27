package com.crosspaste.config

import com.crosspaste.config.AppConfig.Companion.toBoolean
import com.crosspaste.config.AppConfig.Companion.toInt
import com.crosspaste.config.AppConfig.Companion.toLong
import com.crosspaste.config.AppConfig.Companion.toString
import com.crosspaste.ui.theme.ColorContrast
import com.crosspaste.ui.theme.DesktopSearchWindowStyle
import com.crosspaste.ui.theme.SeaColor
import kotlinx.serialization.Serializable

@Serializable
data class DesktopAppConfig(
    override val appInstanceId: String,
    override val language: String,
    override val font: String = "",
    val enableAutoStartUp: Boolean = true,
    val enableDebugMode: Boolean = false,
    override val isFollowSystemTheme: Boolean = true,
    override val isDarkTheme: Boolean = false,
    override val themeColor: String = SeaColor.name,
    override val colorContrast: String = ColorContrast.Standard.name,
    override val port: Int = 13129,
    override val enableEncryptSync: Boolean = false,
    override val enableExpirationCleanup: Boolean = true,
    override val imageCleanTimeIndex: Int = 6,
    override val fileCleanTimeIndex: Int = 6,
    override val enableThresholdCleanup: Boolean = true,
    // MB
    override val maxStorage: Long = 2048,
    override val cleanupPercentage: Int = 20,
    override val enableDiscovery: Boolean = true,
    override val blacklist: String = "[]",
    override val enableSkipPreLaunchPasteboardContent: Boolean = true,
    override val lastPasteboardChangeCount: Int = -1,
    override val enablePasteboardListening: Boolean = true,
    val showTutorial: Boolean = true,
    // MB
    override val maxBackupFileSize: Long = 32,
    override val enabledSyncFileSizeLimit: Boolean = true,
    override val maxSyncFileSize: Long = 512,
    override val useDefaultStoragePath: Boolean = true,
    override val storagePath: String = "",
    override val enableSoundEffect: Boolean = true,
    val searchWindowStyle: String = DesktopSearchWindowStyle.SIDE_STYLE.style,
    val legacySoftwareCompatibility: Boolean = false,
    override val pastePrimaryTypeOnly: Boolean = true,
    override val useNetworkInterfaces: String = "[]",
) : AppConfig {
    override fun copy(
        key: String,
        value: Any,
    ): DesktopAppConfig =
        this.copy(
            appInstanceId = appInstanceId,
            language = if (key == "language") toString(value) else language,
            font = if (key == "font") toString(value) else font,
            enableAutoStartUp = if (key == "enableAutoStartUp") toBoolean(value) else enableAutoStartUp,
            enableDebugMode = if (key == "enableDebugMode") toBoolean(value) else enableDebugMode,
            isFollowSystemTheme = if (key == "isFollowSystemTheme") toBoolean(value) else isFollowSystemTheme,
            isDarkTheme = if (key == "isDarkTheme") toBoolean(value) else isDarkTheme,
            themeColor = if (key == "themeColor") toString(value) else themeColor,
            colorContrast = if (key == "colorContrast") toString(value) else colorContrast,
            port = if (key == "port") toInt(value) else port,
            enableEncryptSync = if (key == "enableEncryptSync") toBoolean(value) else enableEncryptSync,
            enableExpirationCleanup =
                if (key == "enableExpirationCleanup") {
                    toBoolean(
                        value,
                    )
                } else {
                    enableExpirationCleanup
                },
            imageCleanTimeIndex = if (key == "imageCleanTimeIndex") toInt(value) else imageCleanTimeIndex,
            fileCleanTimeIndex = if (key == "fileCleanTimeIndex") toInt(value) else fileCleanTimeIndex,
            enableThresholdCleanup =
                if (key == "enableThresholdCleanup") {
                    toBoolean(
                        value,
                    )
                } else {
                    enableThresholdCleanup
                },
            maxStorage = if (key == "maxStorage") toLong(value) else maxStorage,
            cleanupPercentage = if (key == "cleanupPercentage") toInt(value) else cleanupPercentage,
            blacklist = if (key == "blacklist") toString(value) else blacklist,
            enableSkipPreLaunchPasteboardContent =
                if (key == "enableSkipPreLaunchPasteboardContent") {
                    toBoolean(value)
                } else {
                    enableSkipPreLaunchPasteboardContent
                },
            lastPasteboardChangeCount =
                if (key == "lastPasteboardChangeCount") {
                    toInt(
                        value,
                    )
                } else {
                    lastPasteboardChangeCount
                },
            enablePasteboardListening =
                if (key == "enablePasteboardListening") {
                    toBoolean(
                        value,
                    )
                } else {
                    enablePasteboardListening
                },
            showTutorial = if (key == "showTutorial") toBoolean(value) else showTutorial,
            maxBackupFileSize = if (key == "maxBackupFileSize") toLong(value) else maxBackupFileSize,
            enabledSyncFileSizeLimit =
                if (key == "enabledSyncFileSizeLimit") {
                    toBoolean(
                        value,
                    )
                } else {
                    enabledSyncFileSizeLimit
                },
            maxSyncFileSize = if (key == "maxSyncFileSize") toLong(value) else maxSyncFileSize,
            useDefaultStoragePath = if (key == "useDefaultStoragePath") toBoolean(value) else useDefaultStoragePath,
            storagePath = if (key == "storagePath") toString(value) else storagePath,
            enableSoundEffect = if (key == "enableSoundEffect") toBoolean(value) else enableSoundEffect,
            searchWindowStyle = if (key == "searchWindowStyle") toString(value) else searchWindowStyle,
            legacySoftwareCompatibility =
                if (key == "legacySoftwareCompatibility") {
                    toBoolean(value)
                } else {
                    legacySoftwareCompatibility
                },
            pastePrimaryTypeOnly = if (key == "pastePrimaryTypeOnly") toBoolean(value) else pastePrimaryTypeOnly,
            useNetworkInterfaces = if (key == "useNetworkInterfaces") toString(value) else useNetworkInterfaces,
        )
}
