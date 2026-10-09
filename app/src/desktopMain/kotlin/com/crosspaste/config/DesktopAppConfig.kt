package com.crosspaste.config

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.crosspaste.config.AppConfigKeys.BLACKLIST
import com.crosspaste.config.AppConfigKeys.CLEANUP_PERCENTAGE
import com.crosspaste.config.AppConfigKeys.ENABLED_SYNC_FILE_SIZE_LIMIT
import com.crosspaste.config.AppConfigKeys.ENABLE_DISCOVERY
import com.crosspaste.config.AppConfigKeys.ENABLE_ENCRYPT_SYNC
import com.crosspaste.config.AppConfigKeys.ENABLE_EXPIRATION_CLEANUP
import com.crosspaste.config.AppConfigKeys.ENABLE_PASTEBOARD_LISTENING
import com.crosspaste.config.AppConfigKeys.ENABLE_REMOTE_SHOW_PAIRING_CODE
import com.crosspaste.config.AppConfigKeys.ENABLE_SKIP_PRE_LAUNCH_PASTEBOARD_CONTENT
import com.crosspaste.config.AppConfigKeys.ENABLE_SOUND_EFFECT
import com.crosspaste.config.AppConfigKeys.ENABLE_SYNC_COLOR
import com.crosspaste.config.AppConfigKeys.ENABLE_SYNC_FILE
import com.crosspaste.config.AppConfigKeys.ENABLE_SYNC_HTML
import com.crosspaste.config.AppConfigKeys.ENABLE_SYNC_IMAGE
import com.crosspaste.config.AppConfigKeys.ENABLE_SYNC_RTF
import com.crosspaste.config.AppConfigKeys.ENABLE_SYNC_TEXT
import com.crosspaste.config.AppConfigKeys.ENABLE_SYNC_URL
import com.crosspaste.config.AppConfigKeys.ENABLE_THRESHOLD_CLEANUP
import com.crosspaste.config.AppConfigKeys.ENABLE_URL_PREVIEW
import com.crosspaste.config.AppConfigKeys.FILE_CLEAN_TIME_INDEX
import com.crosspaste.config.AppConfigKeys.FONT
import com.crosspaste.config.AppConfigKeys.IMAGE_CLEAN_TIME_INDEX
import com.crosspaste.config.AppConfigKeys.IS_DARK_THEME
import com.crosspaste.config.AppConfigKeys.IS_FOLLOW_SYSTEM_THEME
import com.crosspaste.config.AppConfigKeys.LANGUAGE
import com.crosspaste.config.AppConfigKeys.LARGE_FILE_DESTINATION_PATH
import com.crosspaste.config.AppConfigKeys.LAST_PASTEBOARD_CHANGE_COUNT
import com.crosspaste.config.AppConfigKeys.MAX_BACKUP_FILE_SIZE
import com.crosspaste.config.AppConfigKeys.MAX_NON_FILE_PASTE_SIZE
import com.crosspaste.config.AppConfigKeys.MAX_STORAGE
import com.crosspaste.config.AppConfigKeys.MAX_SYNC_FILE_SIZE
import com.crosspaste.config.AppConfigKeys.PASTE_PRIMARY_TYPE_ONLY
import com.crosspaste.config.AppConfigKeys.PORT
import com.crosspaste.config.AppConfigKeys.STORAGE_PATH
import com.crosspaste.config.AppConfigKeys.USE_DEFAULT_STORAGE_PATH
import com.crosspaste.config.AppConfigKeys.USE_NETWORK_INTERFACES
import com.crosspaste.config.DesktopConfigKeys.AUTO_DOWNLOAD_UPDATE
import com.crosspaste.config.DesktopConfigKeys.ENABLE_AUTO_START_UP
import com.crosspaste.config.DesktopConfigKeys.ENABLE_CLIPBOARD_RELAY
import com.crosspaste.config.DesktopConfigKeys.ENABLE_DEBUG_MODE
import com.crosspaste.config.DesktopConfigKeys.ENABLE_MCP_SERVER
import com.crosspaste.config.DesktopConfigKeys.LAST_SEEN_CHANGELOG_VERSION
import com.crosspaste.config.DesktopConfigKeys.LEGACY_SOFTWARE_COMPATIBILITY
import com.crosspaste.config.DesktopConfigKeys.LINUX_REMOTE_DESKTOP_RESTORE_TOKEN
import com.crosspaste.config.DesktopConfigKeys.MCP_SERVER_PORT
import com.crosspaste.config.DesktopConfigKeys.NETWORK_BLOCKING_DISMISSED_FINGERPRINT
import com.crosspaste.config.DesktopConfigKeys.OCR_LANGUAGE
import com.crosspaste.config.DesktopConfigKeys.PASTE_PANEL_BUTTON_SIZE
import com.crosspaste.config.DesktopConfigKeys.PROXY_HOST
import com.crosspaste.config.DesktopConfigKeys.PROXY_PORT
import com.crosspaste.config.DesktopConfigKeys.PROXY_TYPE
import com.crosspaste.config.DesktopConfigKeys.SEARCH_WINDOW_HEIGHT
import com.crosspaste.config.DesktopConfigKeys.SHOW_DOCK_ICON
import com.crosspaste.config.DesktopConfigKeys.SHOW_GRANT_ACCESSIBILITY
import com.crosspaste.config.DesktopConfigKeys.SHOW_INSTALL_CLI_PROMPT
import com.crosspaste.config.DesktopConfigKeys.SHOW_PASTE_PANEL_BUTTON
import com.crosspaste.config.DesktopConfigKeys.SHOW_TUTORIAL
import com.crosspaste.config.DesktopConfigKeys.SOURCE_EXCLUSIONS
import com.crosspaste.config.DesktopConfigKeys.SOURCE_EXCLUSION_PATTERNS
import com.crosspaste.config.DesktopConfigKeys.USE_MANUAL_PROXY
import com.crosspaste.ui.extension.ProxyType
import kotlinx.serialization.Serializable

/**
 * Snapshot of the desktop configuration. Persisted per key through
 * [DesktopConfigKeys]; the serializable shape is kept for reading the legacy
 * single-document `appConfig.json`.
 */
@Serializable
data class DesktopAppConfig(
    override val language: String,
    override val font: String = "",
    val enableAutoStartUp: Boolean = true,
    val enableDebugMode: Boolean = false,
    override val isFollowSystemTheme: Boolean = true,
    override val isDarkTheme: Boolean = false,
    // Side search window height in dp, clamped by DesktopAppSize at apply time
    val searchWindowHeight: Int = 332,
    // macOS only: show the app icon in the Dock while the main window is open
    val showDockIcon: Boolean = true,
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
    val sourceExclusions: String = "[]",
    // JSON list of case-insensitive substrings; a source containing any of them is excluded.
    val sourceExclusionPatterns: String = "[]",
    val showTutorial: Boolean = true,
    // MB
    override val maxBackupFileSize: Long = 32,
    override val largeFileDestinationPath: String = "",
    override val enabledSyncFileSizeLimit: Boolean = true,
    override val maxSyncFileSize: Long = 512,
    // MB
    override val maxNonFilePasteSize: Long = 8,
    override val useDefaultStoragePath: Boolean = true,
    override val storagePath: String = "",
    override val enableSoundEffect: Boolean = true,
    override val enableUrlPreview: Boolean = true,
    val legacySoftwareCompatibility: Boolean = false,
    override val pastePrimaryTypeOnly: Boolean = true,
    override val useNetworkInterfaces: String = "[]",
    val ocrLanguage: String = "",
    val useManualProxy: Boolean = false,
    val proxyType: String = ProxyType.HTTP,
    val proxyHost: String = "127.0.0.1",
    val proxyPort: String = "7890",
    val showGrantAccessibility: Boolean = true,
    // One-time macOS prompt offering to install the /usr/local/bin/crosspaste
    // CLI symlink; cleared after the user installs or dismisses it once.
    val showInstallCliPrompt: Boolean = true,
    // Floating button that opens the paste panel; toggled by the show_paste_panel shortcut
    val showPastePanelButton: Boolean = false,
    // "normal" or "small", see DesktopAppSize.PASTE_PANEL_BUTTON_SIZE_*
    val pastePanelButtonSize: String = "normal",
    val enableClipboardRelay: Boolean = false,
    // Sync content type controls
    override val enableSyncText: Boolean = true,
    override val enableSyncUrl: Boolean = true,
    override val enableSyncHtml: Boolean = true,
    override val enableSyncRtf: Boolean = true,
    override val enableSyncImage: Boolean = true,
    override val enableSyncFile: Boolean = true,
    override val enableSyncColor: Boolean = true,
    override val enableRemoteShowPairingCode: Boolean = true,
    // MCP server
    val enableMcpServer: Boolean = false,
    val mcpServerPort: Int = 0,
    // Fingerprint of the most recently dismissed network-blocking diagnosis ("profile|mDnsAllowed"),
    // or empty when the user has not dismissed the current warning.
    val networkBlockingDismissedFingerprint: String = "",
    // Highest app version whose changelog the user has already seen. Empty until seeded on
    // first launch; drives the highlight badge on the changelog menu entry after an upgrade.
    val lastSeenChangelogVersion: String = "",
    // Linux/Wayland: single-use token handed back by the RemoteDesktop portal so the
    // paste-injection session can be restored without prompting the user again.
    val linuxRemoteDesktopRestoreToken: String = "",
    // Windows portable zip only: download a newer release in the background (throttled)
    // as soon as the periodic check finds one, so the prompt can offer a one-click restart.
    val autoDownloadUpdate: Boolean = true,
) : AppConfig {

    /** [DesktopConfigKeys.schema] resolves and coerces [value]; an unknown [key] throws. */
    override fun copy(
        key: String,
        value: Any,
    ): DesktopAppConfig =
        fromPreferences(
            preferences = mutablePreferencesOf().also { DesktopConfigKeys.schema.key(key).write(it, value) },
            defaults = this,
        )

    companion object {

        /** Builds a snapshot from stored values, taking every unset key from [defaults]. */
        fun fromPreferences(
            preferences: Preferences,
            defaults: DesktopAppConfig,
        ): DesktopAppConfig =
            DesktopAppConfig(
                language = preferences[LANGUAGE] ?: defaults.language,
                font = preferences[FONT] ?: defaults.font,
                enableAutoStartUp = preferences[ENABLE_AUTO_START_UP] ?: defaults.enableAutoStartUp,
                enableDebugMode = preferences[ENABLE_DEBUG_MODE] ?: defaults.enableDebugMode,
                isFollowSystemTheme = preferences[IS_FOLLOW_SYSTEM_THEME] ?: defaults.isFollowSystemTheme,
                isDarkTheme = preferences[IS_DARK_THEME] ?: defaults.isDarkTheme,
                searchWindowHeight = preferences[SEARCH_WINDOW_HEIGHT] ?: defaults.searchWindowHeight,
                showDockIcon = preferences[SHOW_DOCK_ICON] ?: defaults.showDockIcon,
                port = preferences[PORT] ?: defaults.port,
                enableEncryptSync = preferences[ENABLE_ENCRYPT_SYNC] ?: defaults.enableEncryptSync,
                enableExpirationCleanup = preferences[ENABLE_EXPIRATION_CLEANUP] ?: defaults.enableExpirationCleanup,
                imageCleanTimeIndex = preferences[IMAGE_CLEAN_TIME_INDEX] ?: defaults.imageCleanTimeIndex,
                fileCleanTimeIndex = preferences[FILE_CLEAN_TIME_INDEX] ?: defaults.fileCleanTimeIndex,
                enableThresholdCleanup = preferences[ENABLE_THRESHOLD_CLEANUP] ?: defaults.enableThresholdCleanup,
                maxStorage = preferences[MAX_STORAGE] ?: defaults.maxStorage,
                cleanupPercentage = preferences[CLEANUP_PERCENTAGE] ?: defaults.cleanupPercentage,
                enableDiscovery = preferences[ENABLE_DISCOVERY] ?: defaults.enableDiscovery,
                blacklist = preferences[BLACKLIST] ?: defaults.blacklist,
                enableSkipPreLaunchPasteboardContent =
                    preferences[ENABLE_SKIP_PRE_LAUNCH_PASTEBOARD_CONTENT]
                        ?: defaults.enableSkipPreLaunchPasteboardContent,
                lastPasteboardChangeCount =
                    preferences[LAST_PASTEBOARD_CHANGE_COUNT] ?: defaults.lastPasteboardChangeCount,
                enablePasteboardListening =
                    preferences[ENABLE_PASTEBOARD_LISTENING] ?: defaults.enablePasteboardListening,
                sourceExclusions = preferences[SOURCE_EXCLUSIONS] ?: defaults.sourceExclusions,
                sourceExclusionPatterns = preferences[SOURCE_EXCLUSION_PATTERNS] ?: defaults.sourceExclusionPatterns,
                showTutorial = preferences[SHOW_TUTORIAL] ?: defaults.showTutorial,
                maxBackupFileSize = preferences[MAX_BACKUP_FILE_SIZE] ?: defaults.maxBackupFileSize,
                largeFileDestinationPath =
                    preferences[LARGE_FILE_DESTINATION_PATH] ?: defaults.largeFileDestinationPath,
                enabledSyncFileSizeLimit =
                    preferences[ENABLED_SYNC_FILE_SIZE_LIMIT] ?: defaults.enabledSyncFileSizeLimit,
                maxSyncFileSize = preferences[MAX_SYNC_FILE_SIZE] ?: defaults.maxSyncFileSize,
                maxNonFilePasteSize = preferences[MAX_NON_FILE_PASTE_SIZE] ?: defaults.maxNonFilePasteSize,
                useDefaultStoragePath = preferences[USE_DEFAULT_STORAGE_PATH] ?: defaults.useDefaultStoragePath,
                storagePath = preferences[STORAGE_PATH] ?: defaults.storagePath,
                enableSoundEffect = preferences[ENABLE_SOUND_EFFECT] ?: defaults.enableSoundEffect,
                enableUrlPreview = preferences[ENABLE_URL_PREVIEW] ?: defaults.enableUrlPreview,
                legacySoftwareCompatibility =
                    preferences[LEGACY_SOFTWARE_COMPATIBILITY] ?: defaults.legacySoftwareCompatibility,
                pastePrimaryTypeOnly = preferences[PASTE_PRIMARY_TYPE_ONLY] ?: defaults.pastePrimaryTypeOnly,
                useNetworkInterfaces = preferences[USE_NETWORK_INTERFACES] ?: defaults.useNetworkInterfaces,
                ocrLanguage = preferences[OCR_LANGUAGE] ?: defaults.ocrLanguage,
                useManualProxy = preferences[USE_MANUAL_PROXY] ?: defaults.useManualProxy,
                proxyType = preferences[PROXY_TYPE] ?: defaults.proxyType,
                proxyHost = preferences[PROXY_HOST] ?: defaults.proxyHost,
                proxyPort = preferences[PROXY_PORT] ?: defaults.proxyPort,
                showGrantAccessibility = preferences[SHOW_GRANT_ACCESSIBILITY] ?: defaults.showGrantAccessibility,
                showInstallCliPrompt = preferences[SHOW_INSTALL_CLI_PROMPT] ?: defaults.showInstallCliPrompt,
                showPastePanelButton = preferences[SHOW_PASTE_PANEL_BUTTON] ?: defaults.showPastePanelButton,
                pastePanelButtonSize = preferences[PASTE_PANEL_BUTTON_SIZE] ?: defaults.pastePanelButtonSize,
                enableClipboardRelay = preferences[ENABLE_CLIPBOARD_RELAY] ?: defaults.enableClipboardRelay,
                enableSyncText = preferences[ENABLE_SYNC_TEXT] ?: defaults.enableSyncText,
                enableSyncUrl = preferences[ENABLE_SYNC_URL] ?: defaults.enableSyncUrl,
                enableSyncHtml = preferences[ENABLE_SYNC_HTML] ?: defaults.enableSyncHtml,
                enableSyncRtf = preferences[ENABLE_SYNC_RTF] ?: defaults.enableSyncRtf,
                enableSyncImage = preferences[ENABLE_SYNC_IMAGE] ?: defaults.enableSyncImage,
                enableSyncFile = preferences[ENABLE_SYNC_FILE] ?: defaults.enableSyncFile,
                enableSyncColor = preferences[ENABLE_SYNC_COLOR] ?: defaults.enableSyncColor,
                enableRemoteShowPairingCode =
                    preferences[ENABLE_REMOTE_SHOW_PAIRING_CODE] ?: defaults.enableRemoteShowPairingCode,
                enableMcpServer = preferences[ENABLE_MCP_SERVER] ?: defaults.enableMcpServer,
                mcpServerPort = preferences[MCP_SERVER_PORT] ?: defaults.mcpServerPort,
                networkBlockingDismissedFingerprint =
                    preferences[NETWORK_BLOCKING_DISMISSED_FINGERPRINT] ?: defaults.networkBlockingDismissedFingerprint,
                lastSeenChangelogVersion =
                    preferences[LAST_SEEN_CHANGELOG_VERSION] ?: defaults.lastSeenChangelogVersion,
                linuxRemoteDesktopRestoreToken =
                    preferences[LINUX_REMOTE_DESKTOP_RESTORE_TOKEN] ?: defaults.linuxRemoteDesktopRestoreToken,
                autoDownloadUpdate = preferences[AUTO_DOWNLOAD_UPDATE] ?: defaults.autoDownloadUpdate,
            )
    }
}
