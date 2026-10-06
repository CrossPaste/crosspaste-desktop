package com.crosspaste.app

import androidx.compose.ui.awt.ComposeWindow
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.listener.DesktopShortcutKeys.Companion.PASTE
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.listener.ShortcutKeysAction
import com.crosspaste.listener.ShortcutKeysListener
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.linux.LinuxActiveAppResolver
import com.crosspaste.platform.linux.LinuxDesktopAppIcon
import com.crosspaste.platform.linux.LinuxDesktopEntry
import com.crosspaste.platform.linux.api.PortalHandles
import com.crosspaste.platform.linux.api.RemoteDesktopPortalKeyboard
import com.crosspaste.platform.linux.api.X11Api
import com.crosspaste.platform.linux.api.X11Api.Companion.bringToBack
import com.sun.jna.NativeLong
import com.sun.jna.platform.unix.X11.Window
import io.ktor.util.collections.ConcurrentSet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okio.Path
import okio.Path.Companion.toPath
import kotlin.time.Duration.Companion.milliseconds

class LinuxAppWindowManager(
    private val appInfo: AppInfo,
    appSize: DesktopAppSize,
    private val lazyConfigManager: Lazy<DesktopConfigManager>,
    private val lazyShortcutKeys: Lazy<ShortcutKeys>,
    private val lazyShortcutKeysAction: Lazy<ShortcutKeysAction>,
    private val lazyShortcutKeysListener: Lazy<ShortcutKeysListener>,
    private val lazyNotificationManager: Lazy<NotificationManager>,
    private val userDataPathProvider: UserDataPathProvider,
) : DesktopAppWindowManager(appSize) {

    private val prevLinuxAppInfo: MutableStateFlow<LinuxAppInfo?> = MutableStateFlow(null)

    // Wayland sessions resolve the focused app through compositor IPC where
    // available; X11 sessions (and the paste-back focus logic below, which is
    // X11-only either way) keep the _NET_ACTIVE_WINDOW path.
    private val activeAppResolver = LinuxActiveAppResolver.detect()

    // Paste-back targets the X11 focus, so outside an X11 session the answer can
    // be a stale one. See [pasteThroughPortal].
    private val isWaylandSession = LinuxActiveAppResolver.isWaylandSession()

    private val portalKeyboard by lazy {
        RemoteDesktopPortalKeyboard(
            loadRestoreToken = { lazyConfigManager.value.config.value.linuxRemoteDesktopRestoreToken },
            saveRestoreToken = { lazyConfigManager.value.updateConfig("linuxRemoteDesktopRestoreToken", it) },
        )
    }

    private val classNameSet: MutableSet<String> = ConcurrentSet()

    private var _cachedMainWindow: Window? = null
    private var _cachedSearchWindow: Window? = null
    private var _cachedBubbleWindow: Window? = null

    val mainWindow: Window?
        get() {
            if (_cachedMainWindow == null) {
                _cachedMainWindow = X11Api.getWindow(mainWindowTitle)
            }
            return _cachedMainWindow
        }

    val searchWindow: Window?
        get() {
            if (_cachedSearchWindow == null) {
                _cachedSearchWindow = X11Api.getWindow(searchWindowTitle)
            }
            return _cachedSearchWindow
        }

    val bubbleWindow: Window?
        get() {
            if (_cachedBubbleWindow == null) {
                _cachedBubbleWindow = X11Api.getWindow(bubbleWindowTitle)
            }
            return _cachedBubbleWindow
        }

    override fun getCurrentActiveAppName(): String? =
        activeAppResolver.getActiveApp()?.let { activeApp ->
            registerApp(activeApp.appName, activeApp.x11Window)
        }

    override fun getRunningAppNames(): List<String> =
        runCatching {
            X11Api
                .getRunningWindows()
                .map { linuxAppInfo ->
                    getAppName(linuxAppInfo)
                }.distinct()
                .sorted()
        }.getOrElse { e ->
            logger.error(e) { "Failed to get running applications" }
            emptyList()
        }

    override fun getPrevAppName(): Flow<String?> =
        prevLinuxAppInfo.map { appInfo ->
            appInfo?.let {
                getAppName(appInfo)
            }
        }

    override val appPickerExtensions: Set<String> = setOf("desktop")

    override val appPickerDirectory: Path = "/usr/share/applications".toPath()

    override fun resolveAppSource(appPath: Path): String? {
        if (!appPath.name.endsWith(".desktop", ignoreCase = true)) return null
        val content = runCatching { appPath.toFile().readText() }.getOrNull() ?: return null
        val appName = LinuxDesktopEntry.sourceName(content, appPath.name)
        runCatching {
            val iconPath = userDataPathProvider.resolveIconPath(appInfo.appInstanceId, appName)
            if (!iconPath.toFile().exists() &&
                !LinuxDesktopAppIcon.saveAppIconFromDesktopFile(appPath.toNioPath(), iconPath.toNioPath())
            ) {
                logger.debug { "No desktop-entry icon found in $appPath" }
            }
        }.onFailure { e ->
            logger.warn(e) { "Failed to save app icon for $appName" }
        }
        return appName
    }

    private fun getAppName(linuxAppInfo: LinuxAppInfo): String =
        registerApp(linuxAppInfo.className, linuxAppInfo.window)

    private fun registerApp(
        appName: String,
        window: Window?,
    ): String {
        if (!classNameSet.contains(appName)) {
            ioScope.launch {
                saveAppImage(appName, window)
            }
            classNameSet.add(appName)
        }
        return appName
    }

    @Synchronized
    private fun saveAppImage(
        appName: String,
        window: Window?,
    ) {
        runCatching {
            val iconPath = userDataPathProvider.resolveIconPath(appInfo.appInstanceId, appName)
            if (iconPath.toFile().exists()) {
                return
            }
            if (window != null) {
                X11Api.saveAppIcon(window, iconPath.toNioPath())
            } else if (!LinuxDesktopAppIcon.saveAppIcon(appName, iconPath.toNioPath())) {
                logger.debug { "No desktop-entry icon found for $appName" }
            }
        }.onFailure { e ->
            logger.warn(e) { "Failed to save app icon for $appName" }
        }
    }

    override fun startWindowService() {
        // do nothing
    }

    override fun stopWindowService() {
        portalKeyboard.close()
    }

    override fun saveCurrentActiveAppInfo() {
        prevLinuxAppInfo.value = X11Api.getActiveWindow(requireXInputFocus = isWaylandSession)
    }

    override suspend fun focusMainWindow(windowTrigger: WindowTrigger) {
        if (windowTrigger == WindowTrigger.SHORTCUT) {
            val xServerTime = lazyShortcutKeysAction.value.event?.`when`
            X11Api.bringToFront(mainWindow, source = NativeLong(2), xServerTime?.let { NativeLong(it) })
        } else {
            X11Api.bringToFront(mainWindow, source = NativeLong(1))
        }
    }

    override suspend fun hideMainWindowAndPaste(preparePaste: suspend () -> Boolean) {
        logger.info { "unActive main window" }
        val toPaste = preparePaste()
        if (toPaste && needsPortalPaste()) {
            // The main window holds the focus, so it has to be gone before the keystroke.
            hideMainWindow()
            delay(FOCUS_RETURN_DELAY)
            pasteThroughPortal()
            return
        }
        bringToBack(toPaste)
        hideMainWindow()
    }

    override suspend fun focusSearchWindow(windowTrigger: WindowTrigger) {
        if (windowTrigger == WindowTrigger.SHORTCUT) {
            val xServerTime = lazyShortcutKeysAction.value.event?.`when`
            X11Api.bringToFront(searchWindow, source = NativeLong(2), xServerTime?.let { NativeLong(it) })
        } else {
            X11Api.bringToFront(searchWindow, source = NativeLong(1))
        }
    }

    override suspend fun focusBubbleWindow() {
        X11Api.bringToFront(bubbleWindow, source = NativeLong(1))
    }

    override suspend fun returnFocusToPreviousApp() {
        if (!needsPortalPaste()) {
            prevLinuxAppInfo.value?.let { bringToBack(it) }
        }
    }

    override suspend fun hideSearchWindowAndPaste(
        size: Int,
        preparePaste: suspend (Int) -> Boolean,
    ) {
        logger.info { "unActive search window" }
        val toPaste = preparePaste(0)
        if (needsPortalPaste()) {
            hideSearchWindow()
            if (toPaste) {
                delay(FOCUS_RETURN_DELAY)
                if (!pasteThroughPortal()) {
                    return
                }
            }
            for (i in 1 until size) {
                delay(1000.milliseconds)
                if (preparePaste(i)) {
                    pasteThroughPortal()
                }
            }
            return
        }
        bringToBack(toPaste)
        for (i in 1 until size) {
            delay(1000.milliseconds)
            if (preparePaste(i)) {
                toPaste()
            }
        }
        hideSearchWindow()
    }

    /**
     * A Wayland session with no X11 window to hand the paste back to: the app that
     * had the focus is a native Wayland window, out of XTest's reach.
     */
    private fun needsPortalPaste(): Boolean = isWaylandSession && prevLinuxAppInfo.value == null

    private suspend fun bringToBack(toPaste: Boolean) {
        val prevAppInfo = prevLinuxAppInfo.value ?: return
        if (toPaste) {
            bringToBack(prevAppInfo, pasteKeyCodes())
        } else {
            bringToBack(prevAppInfo)
        }
    }

    override suspend fun toPaste() {
        // XTest delivers to whatever holds the X input focus, so an X11 session needs
        // no target of its own. In a Wayland session that focus is withdrawn while a
        // native Wayland window is focused, and injecting would hit a stale X window.
        // The panel never took the focus, so the keystroke can go out right away.
        if (isWaylandSession && X11Api.getActiveWindow(requireXInputFocus = true) == null) {
            pasteThroughPortal()
            return
        }
        lazyShortcutKeysListener.value.beginPasteSuppression(
            ShortcutKeysListener.PASTE_INJECTION_SUPPRESS_TIMEOUT,
        )
        X11Api.toPaste(pasteKeyCodes())
    }

    /**
     * Sends the paste shortcut through the RemoteDesktop portal, the one route into
     * a native Wayland window. The keystroke lands wherever the compositor's focus
     * is, so any window of ours that held it must already be hidden. Falls back to
     * the manual-paste hint when the portal is missing or the user declined it.
     */
    private suspend fun pasteThroughPortal(): Boolean {
        val keyCodes = pasteKeyCodes()
        if (keyCodes.isEmpty()) {
            return false
        }
        lazyShortcutKeysListener.value.beginPasteSuppression(
            ShortcutKeysListener.PASTE_INJECTION_SUPPRESS_TIMEOUT,
        )
        val delivered = portalKeyboard.pressAndRelease(keyCodes.map(PortalHandles::evdevKeycode))
        if (!delivered) {
            notifyManualPasteRequired()
        }
        return delivered
    }

    private fun pasteKeyCodes(): List<Int> =
        lazyShortcutKeys.value.shortcutKeysCore.value.keys[PASTE]
            ?.map { key -> key.rawCode }
            ?: listOf()

    /**
     * Last resort when neither XTest nor the portal can deliver the paste: XTest
     * only reaches X11 and XWayland clients, and injecting blindly would hit
     * whichever X client held the focus last — a window the user is not even
     * looking at. The content is already on the clipboard by this point, so say
     * so and let the user paste it.
     */
    private fun notifyManualPasteRequired() {
        logger.info { "No way to deliver the paste, asking the user to paste manually" }
        lazyNotificationManager.value.sendNotification(
            title = { it.getText("copy_successful") },
            message = { it.getText("paste_manually_required") },
            messageType = MessageType.Info,
        )
    }

    companion object {
        // Time for the compositor to hand the focus back to the user's app after one
        // of our windows unmaps, before the portal keystroke goes out.
        private val FOCUS_RETURN_DELAY = 150.milliseconds
    }

    override fun onMainComposeWindowChanged(window: ComposeWindow?) {
        logger.debug { "Main ComposeWindow changed (Linux), invalidating X11 Window cache" }
        _cachedMainWindow = null
    }

    override fun onSearchComposeWindowChanged(window: ComposeWindow?) {
        logger.debug { "Search ComposeWindow changed (Linux), invalidating X11 Window cache" }
        _cachedSearchWindow = null
    }

    override fun onBubbleComposeWindowChanged(window: ComposeWindow?) {
        logger.debug { "Bubble ComposeWindow changed (Linux), invalidating X11 Window cache" }
        _cachedBubbleWindow = null
    }
}

data class LinuxAppInfo(
    val window: Window,
    val className: String,
) {

    override fun toString(): String = "LinuxAppInfo(window=$window, className='$className')"
}
