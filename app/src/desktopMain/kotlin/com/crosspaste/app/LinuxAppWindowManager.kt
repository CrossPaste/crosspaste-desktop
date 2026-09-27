package com.crosspaste.app

import androidx.compose.ui.awt.ComposeWindow
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
    // be a stale one. See [notifyManualPasteRequired].
    private val isWaylandSession = LinuxActiveAppResolver.isWaylandSession()

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
        // do nothing
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
        bringToBack(preparePaste())
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

    override suspend fun hideSearchWindowAndPaste(
        size: Int,
        preparePaste: suspend (Int) -> Boolean,
    ) {
        logger.info { "unActive search window" }
        bringToBack(preparePaste(0))
        for (i in 1 until size) {
            delay(1000.milliseconds)
            if (preparePaste(i)) {
                toPaste()
            }
        }
        hideSearchWindow()
    }

    private suspend fun bringToBack(toPaste: Boolean) {
        val prevAppInfo = prevLinuxAppInfo.value
        if (toPaste) {
            if (prevAppInfo == null) {
                if (isWaylandSession) {
                    notifyManualPasteRequired()
                }
                return
            }
            bringToBack(prevAppInfo, pasteKeyCodes())
        } else {
            bringToBack(prevAppInfo)
        }
    }

    override suspend fun toPaste() {
        // XTest delivers to whatever holds the X input focus, so an X11 session needs
        // no target of its own. In a Wayland session that focus is withdrawn while a
        // native Wayland window is focused, and injecting would hit a stale X window.
        if (isWaylandSession && X11Api.getActiveWindow(requireXInputFocus = true) == null) {
            notifyManualPasteRequired()
            return
        }
        lazyShortcutKeysListener.value.beginPasteSuppression(
            ShortcutKeysListener.PASTE_INJECTION_SUPPRESS_TIMEOUT,
        )
        X11Api.toPaste(pasteKeyCodes())
    }

    private fun pasteKeyCodes(): List<Int> =
        lazyShortcutKeys.value.shortcutKeysCore.value.keys[PASTE]
            ?.map { key -> key.rawCode }
            ?: listOf()

    /**
     * Auto-paste is a synthetic paste shortcut sent through XTest, which only ever
     * reaches X11 and XWayland clients. While a native Wayland window is focused
     * the X11 input focus is withdrawn, so there is no app we may safely target:
     * injecting anyway would deliver the keystroke to whichever X client happened
     * to hold the focus last, pasting into a window the user is not even looking
     * at. The content is already on the clipboard by this point, so say so and let
     * the user paste it.
     */
    private fun notifyManualPasteRequired() {
        logger.info { "No X11 focus to paste into, asking the user to paste manually" }
        lazyNotificationManager.value.sendNotification(
            title = { it.getText("copy_successful") },
            message = { it.getText("paste_manually_required") },
            messageType = MessageType.Info,
        )
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
