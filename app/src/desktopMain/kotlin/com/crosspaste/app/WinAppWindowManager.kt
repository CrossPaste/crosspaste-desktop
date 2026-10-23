package com.crosspaste.app

import androidx.compose.ui.awt.ComposeWindow
import com.crosspaste.listener.DesktopShortcutKeys.Companion.PASTE
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.listener.ShortcutKeysListener
import com.crosspaste.notification.NotificationManager
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.windows.WinAppInfo
import com.crosspaste.platform.windows.WinAppInfoCaches
import com.crosspaste.platform.windows.WindowFocusRecorder
import com.crosspaste.platform.windows.WindowsFocusUtils
import com.crosspaste.platform.windows.api.User32.Companion.INSTANCE
import com.crosspaste.platform.windows.api.WndEnumProc
import com.crosspaste.ui.awaitWindowShowing
import com.crosspaste.utils.pollUntil
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinDef.HWND
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class WinAppWindowManager(
    appInfo: AppInfo,
    appSize: DesktopAppSize,
    private val lazyShortcutKeys: Lazy<ShortcutKeys>,
    private val lazyShortcutKeysListener: Lazy<ShortcutKeysListener>,
    private val lazyNotificationManager: Lazy<NotificationManager>,
    userDataPathProvider: UserDataPathProvider,
) : DesktopAppWindowManager(appSize) {

    private var _cachedMainHWND: HWND? = null
    private var _cachedSearchHWND: HWND? = null
    private var _cachedBubbleHWND: HWND? = null

    val mainHWND: HWND?
        get() {
            if (_cachedMainHWND == null) {
                _cachedMainHWND = WindowsFocusUtils.findPasteWindow(mainWindowTitle)
            }
            return _cachedMainHWND
        }

    val searchHWND: HWND?
        get() {
            if (_cachedSearchHWND == null) {
                val window = searchComposeWindow?.takeIf { it.isDisplayable } ?: return null
                _cachedSearchHWND = HWND(Native.getWindowPointer(window))
            }
            return _cachedSearchHWND
        }

    fun isSearchWindowForeground(): Boolean = WindowsFocusUtils.isWindowForeground(searchHWND)

    fun isSearchWindowComposing(): Boolean = WindowsFocusUtils.isSearchWindowComposing(searchHWND)

    fun isOutsideSearchWindow(
        x: Int,
        y: Int,
    ): Boolean {
        val bubble = bubbleComposeWindow?.takeIf { it.isShowing }?.let { HWND(Native.getWindowPointer(it)) }
        return WindowsFocusUtils.isOutsideSearchWindow(x, y, searchHWND, bubble)
    }

    fun isSearchWindow(hwnd: HWND?): Boolean = hwnd != null && hwnd == searchHWND

    fun isMainWindowForeground(): Boolean {
        val window = mainComposeWindow?.takeIf { it.isDisplayable } ?: return false
        return WindowsFocusUtils.isWindowForeground(HWND(Native.getWindowPointer(window)))
    }

    fun isBubbleWindowForeground(): Boolean {
        val window = bubbleComposeWindow?.takeIf { it.isDisplayable } ?: return false
        return WindowsFocusUtils.isWindowForeground(HWND(Native.getWindowPointer(window)))
    }

    val bubbleHWND: HWND?
        get() {
            if (_cachedBubbleHWND == null) {
                _cachedBubbleHWND = WindowsFocusUtils.findPasteWindow(bubbleWindowTitle)
            }
            return _cachedBubbleHWND
        }

    private val winAppInfoCaches = WinAppInfoCaches(appInfo, userDataPathProvider, ioScope)

    private val windowFocusRecorder = WindowFocusRecorder()

    fun subscribeForegroundChanges(listener: (HWND?) -> Unit): AutoCloseable =
        windowFocusRecorder.subscribeForegroundChanges(listener)

    override fun getPrevAppName(): Flow<String?> =
        windowFocusRecorder.lastWinAppInfo.map { appInfo ->
            appInfo?.getAppName(winAppInfoCaches)
        }

    /**
     * Resolves the app behind the current clipboard change.
     *
     * Prefers User32 GetClipboardOwner — the window that actually wrote the clipboard — so
     * that background tools writing the clipboard without taking focus (e.g. dictation tools
     * injecting text into another app) are attributed correctly instead of being mistaken for
     * the foreground app. Falls back to the foreground window when the clipboard owner is null
     * (owner already exited or a delayed-rendering clipboard) or its app name cannot be
     * resolved.
     */
    override fun getCurrentActiveAppName(): String? =
        INSTANCE.GetClipboardOwner()?.let { ownerHwnd ->
            WinAppInfo(ownerHwnd).getAppName(winAppInfoCaches)
        } ?: getForegroundAppName()

    private fun getForegroundAppName(): String? =
        WinAppInfo(INSTANCE.GetForegroundWindow()).getAppName(winAppInfoCaches)

    override val appPickerExtensions: Set<String> = setOf("exe")

    override fun resolveAppSource(appPath: Path): String? {
        if (!appPath.name.endsWith(".exe", ignoreCase = true)) return null
        return runCatching { winAppInfoCaches.registerPickedApp(appPath) }
            .onFailure { e -> logger.warn(e) { "Failed to resolve app source for $appPath" } }
            .getOrNull()
    }

    override fun getRunningAppNames(): List<String> {
        val appNames = mutableSetOf<String>()
        val ignoredClasses = setOf("Shell_TrayWnd", "Shell_SecondaryTrayWnd")
        val enumProc =
            object : WndEnumProc {
                override fun callback(
                    hWnd: WinDef.HWND,
                    lParam: com.sun.jna.Pointer?,
                ): Boolean {
                    if (INSTANCE.IsWindowVisible(hWnd)) {
                        val titleLength = INSTANCE.GetWindowTextLengthW(hWnd)
                        if (titleLength > 0) {
                            val buffer = CharArray(512)
                            INSTANCE.GetClassNameW(hWnd, buffer, 512)
                            val className = Native.toString(buffer).trim()
                            if (className !in ignoredClasses) {
                                WinAppInfo(hWnd).getAppName(winAppInfoCaches)?.let { appNames.add(it) }
                            }
                        }
                    }
                    return true
                }
            }
        INSTANCE.EnumWindows(enumProc, null)
        return appNames.sorted()
    }

    override fun startWindowService() {
        windowFocusRecorder.start()
    }

    override fun stopWindowService() {
        windowFocusRecorder.stop()
    }

    override fun saveCurrentActiveAppInfo() {
        // do nothing, the WindowFocusRecorder will update the lastWinAppInfo automatically
    }

    override suspend fun focusMainWindow(windowTrigger: WindowTrigger) {
        // Wait for the window to be ready, otherwise bringToFront may cause the window to fail to get focus
        delay(500.milliseconds)
        WindowsFocusUtils.bringToFront(
            windowFocusRecorder.lastWinAppInfo.value?.getThreadId(winAppInfoCaches),
            mainHWND,
        )
    }

    override suspend fun hideMainWindowAndPaste(preparePaste: suspend () -> Boolean) {
        logger.info { "unActive main window" }
        val toPaste = preparePaste()
        val prevHwnd = windowFocusRecorder.lastWinAppInfo.value?.hwnd
        WindowsFocusUtils.backToBack(mainHWND, prevHwnd)
        if (toPaste) {
            pasteIntoPreviousApp(prevHwnd)
        }
        hideMainWindow()
    }

    override suspend fun focusSearchWindow(windowTrigger: WindowTrigger) {
        val window = searchComposeWindow ?: return
        awaitWindowShowing(window)
        WindowsFocusUtils.activateSearchWindow(searchHWND)
    }

    override suspend fun focusBubbleWindow() {
        // Take ownership as soon as the bubble shows, not after the activation delay:
        // it is what keeps the bubble above the search window.
        pollUntil(BUBBLE_SHOW_TIMEOUT, BUBBLE_SHOW_POLL_INTERVAL) { bubbleComposeWindow?.isShowing == true }
        keepBubbleAboveSearch()
        delay(500.milliseconds)
        raiseBubbleWindow()
    }

    override suspend fun raiseBubbleWindow() {
        keepBubbleAboveSearch()
        WindowsFocusUtils.bringToFront(
            windowFocusRecorder.lastWinAppInfo.value?.getThreadId(winAppInfoCaches),
            bubbleHWND,
        )
    }

    /**
     * Both windows are topmost, so a click on the search window would otherwise stack it
     * over the bubble. The bubble window is destroyed when it hides, which ends the
     * ownership with it.
     */
    private fun keepBubbleAboveSearch() {
        val bubble = bubbleHWND ?: return
        val search = searchHWND ?: return
        WindowsFocusUtils.setOwner(bubble, search)
    }

    override suspend fun returnFocusToPreviousApp() {
        WindowsFocusUtils.backToBack(searchHWND, windowFocusRecorder.lastWinAppInfo.value?.hwnd)
    }

    override suspend fun hideSearchWindowAndPaste(
        size: Int,
        preparePaste: suspend (Int) -> Boolean,
    ) {
        logger.info { "unActive search window" }
        val toPaste = preparePaste(0)
        val prevHwnd = windowFocusRecorder.lastWinAppInfo.value?.hwnd
        WindowsFocusUtils.backToBack(searchHWND, prevHwnd)
        if (toPaste && pasteIntoPreviousApp(prevHwnd)) {
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
     * Sends the paste once [prevHwnd]'s app holds the foreground again, or asks the
     * user to paste manually when SetForegroundWindow did not bring it back. The caller
     * must pass the window it captured before hiding ours: hiding hands the foreground
     * to whatever window is next, and the focus recorder may already have stored that
     * one. With no previous window recorded, any app other than CrossPaste counts.
     */
    private suspend fun pasteIntoPreviousApp(prevHwnd: HWND?): Boolean {
        val foreground =
            awaitForeground {
                prevHwnd?.let(WindowsFocusUtils::isForegroundProcessOf)
                    ?: WindowsFocusUtils.isOtherApplicationForeground()
            }
        if (!foreground) {
            notifyManualPasteRequired(lazyNotificationManager.value, lazyShortcutKeys.value)
            return false
        }
        toPaste()
        return true
    }

    override suspend fun toPaste() {
        val keyCodes =
            lazyShortcutKeys.value.shortcutKeysCore.value.keys[PASTE]?.let {
                it.map { key -> key.rawCode }
            } ?: listOf()

        lazyShortcutKeysListener.value.beginPasteSuppression(
            ShortcutKeysListener.PASTE_INJECTION_SUPPRESS_TIMEOUT,
        )
        WindowsFocusUtils.paste(keyCodes)
    }

    override fun onMainComposeWindowChanged(window: ComposeWindow?) {
        logger.debug { "Main ComposeWindow changed, invalidating HWND cache" }
        _cachedMainHWND = null
    }

    override fun onSearchComposeWindowChanged(window: ComposeWindow?) {
        logger.debug { "Search ComposeWindow changed, invalidating HWND cache" }
        _cachedSearchHWND = null
    }

    override fun onBubbleComposeWindowChanged(window: ComposeWindow?) {
        logger.debug { "Bubble ComposeWindow changed, invalidating HWND cache" }
        _cachedBubbleHWND = null
    }
}

private val BUBBLE_SHOW_TIMEOUT = 1.seconds

private val BUBBLE_SHOW_POLL_INTERVAL = 10.milliseconds
