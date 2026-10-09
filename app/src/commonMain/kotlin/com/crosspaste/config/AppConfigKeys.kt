package com.crosspaste.config

/**
 * Keys of the configuration every platform shares, one per [AppConfig] property.
 * Platforms extend the list with their own keys when building their [ConfigSchema].
 */
object AppConfigKeys {
    val LANGUAGE = stringConfigKey("language")
    val FONT = stringConfigKey("font")
    val IS_FOLLOW_SYSTEM_THEME = booleanConfigKey("isFollowSystemTheme")
    val IS_DARK_THEME = booleanConfigKey("isDarkTheme")
    val PORT = intConfigKey("port")
    val ENABLE_ENCRYPT_SYNC = booleanConfigKey("enableEncryptSync")
    val ENABLE_EXPIRATION_CLEANUP = booleanConfigKey("enableExpirationCleanup")
    val IMAGE_CLEAN_TIME_INDEX = intConfigKey("imageCleanTimeIndex")
    val FILE_CLEAN_TIME_INDEX = intConfigKey("fileCleanTimeIndex")
    val ENABLE_THRESHOLD_CLEANUP = booleanConfigKey("enableThresholdCleanup")
    val MAX_STORAGE = longConfigKey("maxStorage")
    val CLEANUP_PERCENTAGE = intConfigKey("cleanupPercentage")
    val ENABLE_DISCOVERY = booleanConfigKey("enableDiscovery")
    val BLACKLIST = stringConfigKey("blacklist")
    val ENABLE_SKIP_PRE_LAUNCH_PASTEBOARD_CONTENT = booleanConfigKey("enableSkipPreLaunchPasteboardContent")

    // Saved on every pasteboard stop so the next launch can skip content it has
    // already seen: pure bookkeeping, kept out of the settings file.
    val LAST_PASTEBOARD_CHANGE_COUNT = intConfigKey("lastPasteboardChangeCount", ConfigScope.RUNTIME_STATE)
    val ENABLE_PASTEBOARD_LISTENING = booleanConfigKey("enablePasteboardListening")
    val MAX_BACKUP_FILE_SIZE = longConfigKey("maxBackupFileSize")
    val LARGE_FILE_DESTINATION_PATH = stringConfigKey("largeFileDestinationPath")
    val ENABLED_SYNC_FILE_SIZE_LIMIT = booleanConfigKey("enabledSyncFileSizeLimit")
    val MAX_SYNC_FILE_SIZE = longConfigKey("maxSyncFileSize")
    val MAX_NON_FILE_PASTE_SIZE = longConfigKey("maxNonFilePasteSize")
    val USE_DEFAULT_STORAGE_PATH = booleanConfigKey("useDefaultStoragePath")
    val STORAGE_PATH = stringConfigKey("storagePath")
    val ENABLE_SOUND_EFFECT = booleanConfigKey("enableSoundEffect")
    val ENABLE_URL_PREVIEW = booleanConfigKey("enableUrlPreview")
    val PASTE_PRIMARY_TYPE_ONLY = booleanConfigKey("pastePrimaryTypeOnly")
    val USE_NETWORK_INTERFACES = stringConfigKey("useNetworkInterfaces")
    val ENABLE_SYNC_TEXT = booleanConfigKey("enableSyncText")
    val ENABLE_SYNC_URL = booleanConfigKey("enableSyncUrl")
    val ENABLE_SYNC_HTML = booleanConfigKey("enableSyncHtml")
    val ENABLE_SYNC_RTF = booleanConfigKey("enableSyncRtf")
    val ENABLE_SYNC_IMAGE = booleanConfigKey("enableSyncImage")
    val ENABLE_SYNC_FILE = booleanConfigKey("enableSyncFile")
    val ENABLE_SYNC_COLOR = booleanConfigKey("enableSyncColor")
    val ENABLE_REMOTE_SHOW_PAIRING_CODE = booleanConfigKey("enableRemoteShowPairingCode")

    val all: List<ConfigKey<*>> =
        listOf(
            LANGUAGE,
            FONT,
            IS_FOLLOW_SYSTEM_THEME,
            IS_DARK_THEME,
            PORT,
            ENABLE_ENCRYPT_SYNC,
            ENABLE_EXPIRATION_CLEANUP,
            IMAGE_CLEAN_TIME_INDEX,
            FILE_CLEAN_TIME_INDEX,
            ENABLE_THRESHOLD_CLEANUP,
            MAX_STORAGE,
            CLEANUP_PERCENTAGE,
            ENABLE_DISCOVERY,
            BLACKLIST,
            ENABLE_SKIP_PRE_LAUNCH_PASTEBOARD_CONTENT,
            LAST_PASTEBOARD_CHANGE_COUNT,
            ENABLE_PASTEBOARD_LISTENING,
            MAX_BACKUP_FILE_SIZE,
            LARGE_FILE_DESTINATION_PATH,
            ENABLED_SYNC_FILE_SIZE_LIMIT,
            MAX_SYNC_FILE_SIZE,
            MAX_NON_FILE_PASTE_SIZE,
            USE_DEFAULT_STORAGE_PATH,
            STORAGE_PATH,
            ENABLE_SOUND_EFFECT,
            ENABLE_URL_PREVIEW,
            PASTE_PRIMARY_TYPE_ONLY,
            USE_NETWORK_INTERFACES,
            ENABLE_SYNC_TEXT,
            ENABLE_SYNC_URL,
            ENABLE_SYNC_HTML,
            ENABLE_SYNC_RTF,
            ENABLE_SYNC_IMAGE,
            ENABLE_SYNC_FILE,
            ENABLE_SYNC_COLOR,
            ENABLE_REMOTE_SHOW_PAIRING_CODE,
        )
}
