package com.crosspaste.app

import androidx.compose.ui.awt.ComposeWindow
import com.crosspaste.listener.DesktopShortcutKeys.Companion.PASTE
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.windows.WinAppInfo
import com.crosspaste.platform.windows.WinAppInfoCaches
import com.crosspaste.platform.windows.WindowFocusRecorder
import com.crosspaste.platform.windows.api.User32
import com.crosspaste.platform.windows.api.User32.Companion.INSTANCE
import com.crosspaste.platform.windows.api.WndEnumProc
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinDef.HWND
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WinAppWindowManager(
    appSize: DesktopAppSize,
    private val lazyShortcutKeys: Lazy<ShortcutKeys>,
    userDataPathProvider: UserDataPathProvider,
) : DesktopAppWindowManager(appSize) {

    private var _cachedMainHWND: HWND? = null
    private var _cachedSearchHWND: HWND? = null
    private var _cachedBubbleHWND: HWND? = null

    val mainHWND: HWND?
        get() {
            if (_cachedMainHWND == null) {
                _cachedMainHWND = User32.findPasteWindow(mainWindowTitle)
            }
            return _cachedMainHWND
        }

    val searchHWND: HWND?
        get() {
            if (_cachedSearchHWND == null) {
                _cachedSearchHWND = User32.findPasteWindow(searchWindowTitle)
            }
            return _cachedSearchHWND
        }

    val bubbleHWND: HWND?
        get() {
            if (_cachedBubbleHWND == null) {
                _cachedBubbleHWND = User32.findPasteWindow(bubbleWindowTitle)
            }
            return _cachedBubbleHWND
        }

    private val winAppInfoCaches = WinAppInfoCaches(userDataPathProvider, ioScope)

    private val windowFocusRecorder = WindowFocusRecorder(this)

    override fun getPrevAppName(): Flow<String?> =
        windowFocusRecorder.lastWinAppInfo.map { appInfo ->
            appInfo?.getAppName(winAppInfoCaches)
        }

    override fun getCurrentActiveAppName(): String? =
        WinAppInfo(INSTANCE.GetForegroundWindow()).getAppName(winAppInfoCaches)

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
        delay(500)
        User32.bringToFront(
            windowFocusRecorder.lastWinAppInfo.value?.getThreadId(winAppInfoCaches),
            mainHWND,
        )
    }

    override suspend fun hideMainWindowAndPaste(preparePaste: suspend () -> Boolean) {
        logger.info { "unActive main window" }
        bringToBack(preparePaste(), mainHWND)
        hideMainWindow()
    }

    override suspend fun focusSearchWindow(windowTrigger: WindowTrigger) {
        // Wait for the window to be ready, otherwise bringToFront may cause the window to fail to get focus
        delay(500)
        User32.bringToFront(
            windowFocusRecorder.lastWinAppInfo.value?.getThreadId(winAppInfoCaches),
            searchHWND,
        )
    }

    override suspend fun focusBubbleWindow() {
        delay(500)
        User32.bringToFront(
            windowFocusRecorder.lastWinAppInfo.value?.getThreadId(winAppInfoCaches),
            bubbleHWND,
        )
    }

    override suspend fun hideSearchWindowAndPaste(
        size: Int,
        preparePaste: suspend (Int) -> Boolean,
    ) {
        logger.info { "unActive search window" }
        bringToBack(preparePaste(0), searchHWND)
        for (i in 1 until size) {
            delay(1000)
            if (preparePaste(i)) {
                toPaste()
            }
        }
        hideSearchWindow()
    }

    private fun bringToBack(
        toPaste: Boolean,
        backHWND: HWND?,
    ) {
        if (toPaste) {
            val keyCodes =
                lazyShortcutKeys.value.shortcutKeysCore.value.keys[PASTE]?.let {
                    it.map { key -> key.rawCode }
                } ?: listOf()
            User32.bringToBackAndPaste(
                backHWND,
                windowFocusRecorder.lastWinAppInfo.value?.hwnd,
                keyCodes,
            )
        } else {
            User32.backToBack(backHWND, windowFocusRecorder.lastWinAppInfo.value?.hwnd)
        }
    }

    override suspend fun toPaste() {
        val keyCodes =
            lazyShortcutKeys.value.shortcutKeysCore.value.keys[PASTE]?.let {
                it.map { key -> key.rawCode }
            } ?: listOf()

        User32.paste(keyCodes)
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
