package com.crosspaste.app

import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.listener.DesktopShortcutKeys.Companion.PASTE
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.windows.WinAppInfo
import com.crosspaste.platform.windows.WinAppInfoCaches
import com.crosspaste.platform.windows.WindowFocusRecorder
import com.crosspaste.platform.windows.api.User32
import com.crosspaste.platform.windows.api.User32.Companion.INSTANCE
import com.sun.jna.platform.win32.WinDef.HWND
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class WinAppWindowManager(
    appSize: DesktopAppSize,
    configManager: DesktopConfigManager,
    private val lazyShortcutKeys: Lazy<ShortcutKeys>,
    userDataPathProvider: UserDataPathProvider,
) : DesktopAppWindowManager(appSize, configManager) {

    private var prevWinAppInfo: MutableStateFlow<WinAppInfo?> = MutableStateFlow(null)

    val mainHWND: HWND? by lazy {
        User32.findPasteWindow(mainWindowTitle)
    }

    val searchHWND: HWND? by lazy {
        User32.findPasteWindow(searchWindowTitle)
    }

    private val winAppInfoCaches = WinAppInfoCaches(userDataPathProvider, ioScope)

    private val windowFocusRecorder = WindowFocusRecorder(this)

    override fun getPrevAppName(): Flow<String?> =
        windowFocusRecorder.lastWinAppInfo.map { appInfo ->
            appInfo?.getAppName(winAppInfoCaches)
        }

    override fun getCurrentActiveAppName(): String? =
        WinAppInfo(INSTANCE.GetForegroundWindow()).getAppName(winAppInfoCaches)

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
                prevWinAppInfo.value?.hwnd,
                keyCodes,
            )
        } else {
            User32.backToBack(backHWND, prevWinAppInfo.value?.hwnd)
        }
    }

    override suspend fun toPaste() {
        val keyCodes =
            lazyShortcutKeys.value.shortcutKeysCore.value.keys[PASTE]?.let {
                it.map { key -> key.rawCode }
            } ?: listOf()

        User32.paste(keyCodes)
    }

    fun initMenuHWND(): HWND? = User32.findPasteWindow(MENU_WINDOW_TITLE)
}
