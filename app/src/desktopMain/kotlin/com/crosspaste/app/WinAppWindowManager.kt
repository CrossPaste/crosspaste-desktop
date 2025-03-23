package com.crosspaste.app

import androidx.compose.ui.window.WindowState
import com.crosspaste.listen.ActiveGraphicsDevice
import com.crosspaste.listen.DesktopShortcutKeys.Companion.PASTE
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.windows.api.User32
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.sun.jna.platform.win32.WinDef.HWND
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class WinAppWindowManager(
    appSize: AppSize,
    private val lazyShortcutKeys: Lazy<ShortcutKeys>,
    private val activeGraphicsDevice: ActiveGraphicsDevice,
    private val userDataPathProvider: UserDataPathProvider,
) : DesktopAppWindowManager(appSize) {

    private var prevWinAppInfo: MutableStateFlow<WinAppInfo?> = MutableStateFlow(null)

    private val mainHWND: HWND? by lazy {
        User32.findPasteWindow(MAIN_WINDOW_TITLE)
    }

    private val searchHWND: HWND? by lazy {
        User32.findPasteWindow(SEARCH_WINDOW_TITLE)
    }

    private val fileDescriptorCache: LoadingCache<String, String> =
        CacheBuilder.newBuilder()
            .maximumSize(20)
            .build(
                object : CacheLoader<String, String>() {
                    override fun load(filePath: String): String {
                        User32.getFileDescription(filePath)?.let {
                            ioScope.launch {
                                saveAppImage(filePath, it)
                            }
                            return it
                        }
                        return ""
                    }
                },
            )

    @Synchronized
    private fun saveAppImage(
        exeFilePath: String,
        fileDescription: String,
    ) {
        val iconPath = userDataPathProvider.resolve("$fileDescription.png", AppFileType.ICON)
        if (!iconPath.toFile().exists()) {
            User32.extractAndSaveIcon(exeFilePath, iconPath.toString())
        }
    }

    override fun getPrevAppName(): Flow<String?> {
        return prevWinAppInfo.map { appInfo ->
            appInfo?.filePath?.let {
                fileDescriptorCache[it].ifEmpty { null }
            }
        }
    }

    override fun getCurrentActiveAppName(): String? {
        return User32.getActiveWindowProcessFilePath()?.let {
            fileDescriptorCache[it].ifEmpty { null }
        }
    }

    override suspend fun activeMainWindow() {
        logger.info { "active main window" }

        val pair = User32.getForegroundWindowAppInfoAndPid(mainHWND, searchHWND)

        pair?.let {
            prevWinAppInfo.value = it.first
        }

        setShowMainWindow(true)
        delay(500)
        mainFocusRequester.requestFocus()
    }

    override suspend fun unActiveMainWindow(preparePaste: suspend () -> Boolean) {
        logger.info { "unActive main window" }
        bringToBack(preparePaste(), mainHWND)
        setShowMainWindow(false)
        mainFocusRequester.freeFocus()
    }

    override suspend fun activeSearchWindow() {
        logger.info { "active search window" }

        val pair = User32.getForegroundWindowAppInfoAndPid(mainHWND, searchHWND)

        pair?.let {
            prevWinAppInfo.value = it.first
        }

        activeGraphicsDevice.getGraphicsDevice()?.let { graphicsDevice ->
            setSearchWindowState(
                WindowState(
                    size = appSize.searchWindowSize,
                    position = calPosition(graphicsDevice.defaultConfiguration.bounds),
                ),
            )
        }
        setShowSearchWindow(true)

        // Wait for the window to be ready, otherwise bringToFront may cause the window to fail to get focus
        delay(500)

        pair?.let {
            User32.bringToFront(SEARCH_WINDOW_TITLE, pair.second, searchHWND)
        }
        searchFocusRequester.requestFocus()
    }

    override suspend fun unActiveSearchWindow(preparePaste: suspend () -> Boolean) {
        logger.info { "unActive search window" }
        bringToBack(preparePaste(), searchHWND)
        setShowSearchWindow(false)
        searchFocusRequester.freeFocus()
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

    fun initMenuHWND(): HWND? {
        return User32.findPasteWindow(MENU_WINDOW_TITLE)
    }
}

data class WinAppInfo(val hwnd: HWND, val filePath: String) {

    override fun toString(): String {
        return "WinAppInfo(hwnd=$hwnd, filePath='$filePath')"
    }
}
