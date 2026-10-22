package com.crosspaste.app

import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.listener.ShortcutKeysListener
import com.crosspaste.notification.NotificationManager
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.macos.MacAppUtils
import com.crosspaste.platform.macos.MacPasteUtils
import com.crosspaste.utils.getSystemProperty
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okio.Path
import okio.Path.Companion.toPath
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class MacAppWindowManager(
    private val appInfo: AppInfo,
    appSize: DesktopAppSize,
    private val lazyConfigManager: Lazy<DesktopConfigManager>,
    private val lazyShortcutKeys: Lazy<ShortcutKeys>,
    private val lazyShortcutKeysListener: Lazy<ShortcutKeysListener>,
    private val lazyNotificationManager: Lazy<NotificationManager>,
    private val userDataPathProvider: UserDataPathProvider,
) : DesktopAppWindowManager(appSize) {

    private val crosspasteBundleID = getSystemProperty().get("mac.bundleID")

    init {
        // Warms the native activation monitor so the search window's focus rechecks
        // read cached state instead of blocking the EDT on AppKit. The first call
        // waits for the main thread once (on this IO thread, never the UI thread)
        // to register the observer; it relies on this manager being created during
        // startup, before the search window can open.
        ioScope.launch { MacAppUtils.isApplicationActive() }
        // Keep the activation policy in step with the main window and the setting.
        // Demoting to .accessory deactivates the app, so it must happen when the main
        // window hides rather than when the search window is next brought to front:
        // there the deactivation races the new key window and dismisses it.
        // setDockIconVisibility only switches the activation policy — it must not
        // touch window visibility, so a toggle racing a window hide can never
        // re-show the window. No dedup here: bringToFront also writes the policy, so
        // the last value sent is not the native state; re-applying is a no-op.
        ioScope.launch {
            combine(
                mainWindowInfo.map { it.show },
                lazyConfigManager.value.config.map { it.showDockIcon },
            ) { mainShow, showDockIcon -> mainShow to (mainShow && showDockIcon) }
                .collect { (mainShow, dockIconPolicy) ->
                    // Only a demotion from the settings toggle (main window open) must
                    // restore focus; after a hide the previous app keeps it.
                    MacAppUtils.setDockIconVisibility(dockIconPolicy, restoreActivation = mainShow)
                }
        }
    }

    // The Dock icon is a pure function of "main window visible && setting enabled",
    // independent of which window is being focused
    private fun showDockIcon(mainWindowVisible: Boolean): Boolean =
        mainWindowVisible && lazyConfigManager.value.config.value.showDockIcon

    private var prevMacAppInfo: MutableStateFlow<MacAppInfo?> = MutableStateFlow(null)

    private val macPasteUtils: MacPasteUtils by lazy { MacPasteUtils(lazyShortcutKeys.value) }

    override fun getPrevAppName(): Flow<String?> =
        prevMacAppInfo.map { appInfo ->
            appInfo?.localizedName
        }

    override fun getCurrentActiveAppName(): String? =
        runCatching {
            MacAppUtils.getCurrentActiveAppInfo()?.let {
                createMacAppInfo(info = it)?.let { macAppInfo ->
                    ioScope.launch {
                        saveImagePathByApp(macAppInfo.bundleIdentifier, macAppInfo.localizedName)
                    }
                    macAppInfo.localizedName
                }
            }
        }.getOrElse { e ->
            logger.error(e) { "Failed to get current active app name" }
            null
        }

    override fun getRunningAppNames(): List<String> =
        runCatching {
            MacAppUtils
                .getRunningApplications()
                .map { (bundleId, name) ->
                    ioScope.launch { saveImagePathByApp(bundleId, name) }
                    name
                }.distinct()
                .sorted()
        }.getOrElse { e ->
            logger.error(e) { "Failed to get running applications" }
            emptyList()
        }

    override val appPickerExtensions: Set<String> = setOf("app")

    override val appPickerDirectory: Path = "/Applications".toPath()

    override fun resolveAppSource(appPath: Path): String? {
        if (!appPath.name.endsWith(".app", ignoreCase = true)) return null
        val macAppInfo =
            runCatching { MacAppUtils.getAppInfoAtPath(appPath.toString()) }
                .getOrNull()
                ?.let { createMacAppInfo(it) }
                ?: return null
        runCatching {
            val iconPath = userDataPathProvider.resolveIconPath(appInfo.appInstanceId, macAppInfo.localizedName)
            if (!iconPath.toFile().exists() &&
                !MacAppUtils.saveAppIconAtPath(appPath.toString(), iconPath.toString())
            ) {
                logger.debug { "No icon rendered for ${macAppInfo.localizedName}" }
            }
        }.onFailure { e ->
            logger.warn(e) { "Failed to save app icon for ${macAppInfo.localizedName}" }
        }
        return macAppInfo.localizedName
    }

    private fun createMacAppInfo(info: String): MacAppInfo? {
        val result = info.split("\n", limit = 2)
        if (result.size == 2) {
            val bundleIdentifier = result[0]
            val localizedName = result[1]
            return MacAppInfo(bundleIdentifier, localizedName)
        }
        return null
    }

    @Synchronized
    private fun saveImagePathByApp(
        bundleIdentifier: String,
        localizedName: String,
    ) {
        runCatching {
            val appImagePath = userDataPathProvider.resolveIconPath(appInfo.appInstanceId, localizedName)
            if (!appImagePath.toFile().exists()) {
                MacAppUtils.saveAppIcon(bundleIdentifier, appImagePath.toString())
            }
        }.onFailure { e ->
            logger.warn(e) { "Failed to save app icon for $localizedName ($bundleIdentifier)" }
        }
    }

    override fun startWindowService() {
        // do nothing
    }

    override fun stopWindowService() {
        // do nothing
    }

    override fun saveCurrentActiveAppInfo() {
        MacAppUtils.getCurrentActiveAppInfo()?.let {
            createMacAppInfo(it)?.let { macAppInfo ->
                if (macAppInfo.bundleIdentifier != crosspasteBundleID) {
                    prevMacAppInfo.value = macAppInfo
                    logger.info { "save prevAppName $macAppInfo" }
                }
            }
        }
    }

    override suspend fun focusMainWindow(windowTrigger: WindowTrigger) {
        MacAppUtils.bringToFront(mainWindowTitle, showDockIcon(mainWindowVisible = true))
    }

    override suspend fun hideMainWindowAndPaste(preparePaste: suspend () -> Boolean) {
        logger.info { "unActive main window" }
        val toPaste = preparePaste()
        val prevAppId = prevMacAppInfo.value?.bundleIdentifier
        MacAppUtils.mainToBack(prevAppId ?: "")
        if (toPaste) {
            pasteIntoPreviousApp(prevAppId)
        }
        hideMainWindow()
    }

    override suspend fun focusSearchWindow(windowTrigger: WindowTrigger) {
        MacAppUtils.bringToFront(searchWindowTitle, showDockIcon(getCurrentMainWindowInfo().show))
    }

    override suspend fun focusBubbleWindow() {
        MacAppUtils.bringToFront(bubbleWindowTitle, showDockIcon(getCurrentMainWindowInfo().show))
    }

    override suspend fun returnFocusToPreviousApp() {
        MacAppUtils.searchToBack(prevMacAppInfo.value?.bundleIdentifier ?: "")
    }

    override suspend fun hideSearchWindowAndPaste(
        size: Int,
        preparePaste: suspend (Int) -> Boolean,
    ) {
        logger.info { "unActive search window" }
        val toPaste = preparePaste(0)
        val prevAppId = prevMacAppInfo.value?.bundleIdentifier
        MacAppUtils.searchToBack(prevAppId ?: "")
        if (toPaste && pasteIntoPreviousApp(prevAppId)) {
            for (i in 1 until size) {
                delay(1000.milliseconds)
                if (preparePaste(i)) {
                    toPaste()
                }
            }
        }
        hideSearchWindow()
    }

    /**
     * Sends the paste once [prevAppId] is frontmost again, or asks the user to paste
     * manually when it never comes back. The caller passes the same app it asked to
     * activate. With no previous app recorded, any app other than CrossPaste counts.
     */
    private suspend fun pasteIntoPreviousApp(prevAppId: String?): Boolean {
        val activationStart = TimeSource.Monotonic.markNow()
        val foreground =
            awaitForeground {
                val frontmostId = MacAppUtils.getCurrentActiveAppInfo()?.let(::createMacAppInfo)?.bundleIdentifier
                if (prevAppId != null) frontmostId == prevAppId else frontmostId != crosspasteBundleID
            }
        if (!foreground) {
            notifyManualPasteRequired(lazyNotificationManager.value, lazyShortcutKeys.value)
            return false
        }
        // An app can report frontmost before its key window takes input, so always settle
        // briefly after it does. The keystroke used to go out a fixed 200 ms after
        // activation; keep that as a floor too. activationStart is taken when activation
        // is queued on the AppKit main thread, so on a busy main thread most of the floor
        // may already be spent and only the settle time remains.
        delay(maxOf(MIN_ACTIVATION_SETTLE - activationStart.elapsedNow(), FRONTMOST_SETTLE))
        toPaste()
        return true
    }

    override suspend fun toPaste() {
        lazyShortcutKeysListener.value.beginPasteSuppression(
            ShortcutKeysListener.PASTE_INJECTION_SUPPRESS_TIMEOUT,
        )
        macPasteUtils.simulatePasteCommand()
    }
}

private val MIN_ACTIVATION_SETTLE = 200.milliseconds

private val FRONTMOST_SETTLE = 50.milliseconds

private data class MacAppInfo(
    val bundleIdentifier: String,
    val localizedName: String,
) {

    override fun toString(): String = "MacAppInfo(bundleIdentifier='$bundleIdentifier', localizedName='$localizedName')"
}
