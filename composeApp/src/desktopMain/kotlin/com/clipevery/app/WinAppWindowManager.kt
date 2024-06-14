package com.clipevery.app

import com.clipevery.listen.ActiveGraphicsDevice
import com.clipevery.listener.ShortcutKeys
import com.clipevery.os.windows.api.User32
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.sun.jna.platform.win32.WinDef.HWND
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.io.path.absolutePathString

class WinAppWindowManager(
    private val lazyShortcutKeys: Lazy<ShortcutKeys>,
    private val activeGraphicsDevice: ActiveGraphicsDevice,
) : AbstractAppWindowManager() {

    private var prevWinAppInfo: WinAppInfo? = null

    private val mainHWND: HWND? by lazy {
        User32.findClipWindow(MAIN_WINDOW_TITLE)
    }

    private val searchHWND: HWND? by lazy {
        User32.findClipWindow(SEARCH_WINDOW_TITLE)
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
        val iconPath = pathProvider.resolve("$fileDescription.png", AppFileType.ICON)
        if (!iconPath.toFile().exists()) {
            User32.extractAndSaveIcon(exeFilePath, iconPath.absolutePathString())
        }
    }

    override fun getPrevAppName(): String? {
        return prevWinAppInfo?.filePath?.let {
            fileDescriptorCache[it].ifEmpty { null }
        }
    }

    override fun getCurrentActiveAppName(): String? {
        return User32.getActiveWindowProcessFilePath()?.let {
            fileDescriptorCache[it].ifEmpty { null }
        }
    }

    override suspend fun activeMainWindow() {
        logger.info { "active main window" }
        showMainWindow = true
        prevWinAppInfo = User32.bringToFront(MAIN_WINDOW_TITLE, mainHWND, searchHWND)
        delay(500)
        mainFocusRequester.requestFocus()
    }

    override suspend fun unActiveMainWindow() {
        logger.info { "unActive main window" }
        val keyCodes =
            lazyShortcutKeys.value.shortcutKeysCore.keys["Paste"]?.let {
                it.map { key -> key.rawCode }
            } ?: listOf()
        User32.bringToBack(MAIN_WINDOW_TITLE, mainHWND, searchHWND, prevWinAppInfo?.hwnd, false, keyCodes)
        showMainWindow = false
        mainFocusRequester.freeFocus()
    }

    override suspend fun activeSearchWindow() {
        logger.info { "active search window" }
        showSearchWindow = true

        activeGraphicsDevice.getGraphicsDevice()?.let { graphicsDevice ->
            searchWindowState.position = calPosition(graphicsDevice.defaultConfiguration.bounds)
        }

        prevWinAppInfo = User32.bringToFront(SEARCH_WINDOW_TITLE, mainHWND, searchHWND)

        delay(500)
        searchFocusRequester.requestFocus()
    }

    override suspend fun unActiveSearchWindow(preparePaste: suspend () -> Boolean) {
        logger.info { "unActive search window" }
        val toPaste = preparePaste()
        val keyCodes =
            lazyShortcutKeys.value.shortcutKeysCore.keys["Paste"]?.let {
                it.map { key -> key.rawCode }
            } ?: listOf()
        User32.bringToBack(SEARCH_WINDOW_TITLE, mainHWND, searchHWND, prevWinAppInfo?.hwnd, toPaste, keyCodes)
        showSearchWindow = false
        searchFocusRequester.freeFocus()
    }

    override suspend fun toPaste() {
        val keyCodes =
            lazyShortcutKeys.value.shortcutKeysCore.keys["Paste"]?.let {
                it.map { key -> key.rawCode }
            } ?: listOf()

        User32.paste(keyCodes)
    }

    fun initMenuHWND(): HWND? {
        return User32.findClipWindow(MENU_WINDOW_TITLE)
    }
}

data class WinAppInfo(val hwnd: HWND, val filePath: String) {

    override fun toString(): String {
        return "WinAppInfo(hwnd=$hwnd, filePath='$filePath')"
    }
}
