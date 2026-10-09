package com.crosspaste.config

/**
 * The desktop-only keys of [DesktopAppConfig], plus the [schema] combining them
 * with the shared [AppConfigKeys]. Keys live in [ConfigScope.SETTINGS] unless they
 * are bookkeeping the app does for itself.
 */
object DesktopConfigKeys {
    val ENABLE_AUTO_START_UP = booleanConfigKey("enableAutoStartUp")
    val ENABLE_DEBUG_MODE = booleanConfigKey("enableDebugMode")
    val SEARCH_WINDOW_HEIGHT = intConfigKey("searchWindowHeight")
    val SHOW_DOCK_ICON = booleanConfigKey("showDockIcon")
    val SOURCE_EXCLUSIONS = stringConfigKey("sourceExclusions")
    val SOURCE_EXCLUSION_PATTERNS = stringConfigKey("sourceExclusionPatterns")
    val SHOW_TUTORIAL = booleanConfigKey("showTutorial")
    val LEGACY_SOFTWARE_COMPATIBILITY = booleanConfigKey("legacySoftwareCompatibility")
    val OCR_LANGUAGE = stringConfigKey("ocrLanguage")
    val USE_MANUAL_PROXY = booleanConfigKey("useManualProxy")
    val PROXY_TYPE = stringConfigKey("proxyType")
    val PROXY_HOST = stringConfigKey("proxyHost")
    val PROXY_PORT = stringConfigKey("proxyPort")
    val SHOW_GRANT_ACCESSIBILITY = booleanConfigKey("showGrantAccessibility")
    val SHOW_INSTALL_CLI_PROMPT = booleanConfigKey("showInstallCliPrompt")
    val SHOW_PASTE_PANEL_BUTTON = booleanConfigKey("showPastePanelButton")
    val PASTE_PANEL_BUTTON_SIZE = stringConfigKey("pastePanelButtonSize")
    val ENABLE_CLIPBOARD_RELAY = booleanConfigKey("enableClipboardRelay")
    val ENABLE_MCP_SERVER = booleanConfigKey("enableMcpServer")
    val MCP_SERVER_PORT = intConfigKey("mcpServerPort")
    val AUTO_DOWNLOAD_UPDATE = booleanConfigKey("autoDownloadUpdate")

    // Runtime state: what the app remembers about its own past, not what the user set.
    val NETWORK_BLOCKING_DISMISSED_FINGERPRINT =
        stringConfigKey("networkBlockingDismissedFingerprint", ConfigScope.RUNTIME_STATE)
    val LAST_SEEN_CHANGELOG_VERSION = stringConfigKey("lastSeenChangelogVersion", ConfigScope.RUNTIME_STATE)
    val LINUX_REMOTE_DESKTOP_RESTORE_TOKEN =
        stringConfigKey("linuxRemoteDesktopRestoreToken", ConfigScope.RUNTIME_STATE)

    val all: List<ConfigKey<*>> =
        listOf(
            ENABLE_AUTO_START_UP,
            ENABLE_DEBUG_MODE,
            SEARCH_WINDOW_HEIGHT,
            SHOW_DOCK_ICON,
            SOURCE_EXCLUSIONS,
            SOURCE_EXCLUSION_PATTERNS,
            SHOW_TUTORIAL,
            LEGACY_SOFTWARE_COMPATIBILITY,
            OCR_LANGUAGE,
            USE_MANUAL_PROXY,
            PROXY_TYPE,
            PROXY_HOST,
            PROXY_PORT,
            SHOW_GRANT_ACCESSIBILITY,
            SHOW_INSTALL_CLI_PROMPT,
            SHOW_PASTE_PANEL_BUTTON,
            PASTE_PANEL_BUTTON_SIZE,
            ENABLE_CLIPBOARD_RELAY,
            ENABLE_MCP_SERVER,
            MCP_SERVER_PORT,
            AUTO_DOWNLOAD_UPDATE,
            NETWORK_BLOCKING_DISMISSED_FINGERPRINT,
            LAST_SEEN_CHANGELOG_VERSION,
            LINUX_REMOTE_DESKTOP_RESTORE_TOKEN,
        )

    val schema = ConfigSchema(AppConfigKeys.all + all)
}
