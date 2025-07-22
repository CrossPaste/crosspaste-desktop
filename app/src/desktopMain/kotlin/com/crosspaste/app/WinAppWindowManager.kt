package com.crosspaste.app

import com.crosspaste.config.DesktopConfigManager
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
    appSize: DesktopAppSize,
    configManager: DesktopConfigManager,
    private val lazyShortcutKeys: Lazy<ShortcutKeys>,
    private val userDataPathProvider: UserDataPathProvider,
) : DesktopAppWindowManager(appSize, configManager) {

    private var prevWinAppInfo: MutableStateFlow<WinAppInfo?> = MutableStateFlow(null)

    val mainHWND: HWND? by lazy {
        User32.findPasteWindow(mainWindowTitle)
    }

    val searchHWND: HWND? by lazy {
        User32.findPasteWindow(searchWindowTitle)
    }

    private val fileDescriptorCache: LoadingCache<String, String> =
        CacheBuilder
            .newBuilder()
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
        val iconFile = iconPath.toFile()
        if (!iconFile.exists()) {
            User32.extractAndSaveIcon(exeFilePath, iconPath.toString())
        }
    }

    override fun getPrevAppName(): Flow<String?> =
        prevWinAppInfo.map { appInfo ->
            appInfo?.filePath?.let {
                fileDescriptorCache[it].ifEmpty { null }
            }
        }

    override fun getCurrentActiveAppName(): String? =
        User32.getActiveWindowProcessFilePath()?.let {
            fileDescriptorCache[it].ifEmpty { null }
        }

    override suspend fun recordActiveInfoAndShowMainWindow(useShortcutKeys: Boolean) {
        logger.info { "active main window" }

        val pair = User32.getForegroundWindowAppInfoAndThreadId(useShortcutKeys)

        pair?.let {
            prevWinAppInfo.value = it.first
        }

        showMainWindow()
    }

    override suspend fun hideMainWindowAndPaste(preparePaste: suspend () -> Boolean) {
        logger.info { "unActive main window" }
        bringToBack(preparePaste(), mainHWND)
        this@WinAppWindowManager.hideMainWindow()
    }

    override suspend fun recordActiveInfoAndShowSearchWindow(useShortcutKeys: Boolean) {
        logger.info { "active search window" }

        val pair = User32.getForegroundWindowAppInfoAndThreadId(!useShortcutKeys)

        pair?.let {
            prevWinAppInfo.value = it.first
        }

        setSearchWindowState(appSize.getSearchWindowState())
        showSearchWindow()

        // Wait for the window to be ready, otherwise bringToFront may cause the window to fail to get focus
        delay(500)

        pair?.let {
            User32.bringToFront(pair.second, searchHWND)
        }
    }

    override suspend fun hideSearchWindowAndPaste(preparePaste: suspend () -> Boolean) {
        logger.info { "unActive search window" }
        bringToBack(preparePaste(), searchHWND)
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

data class WinAppInfo(
    val hwnd: HWND,
    val filePath: String,
) {

    override fun toString(): String = "WinAppInfo(hwnd=$hwnd, filePath='$filePath')"
}
