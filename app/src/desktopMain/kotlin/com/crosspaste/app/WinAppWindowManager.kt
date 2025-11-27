package com.crosspaste.app

import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.listen.DesktopShortcutKeys.Companion.PASTE
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.listener.ShortcutKeysAction
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
    private val lazyShortcutKeysAction: Lazy<ShortcutKeysAction>,
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
        if (!iconPath.toFile().exists()) {
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

    override fun saveCurrentActiveAppInfo() {
        prevWinAppInfo.value = User32.getForegroundWindowAppInfoAndThreadId(lazyShortcutKeysAction.value.actioning)
    }

    override fun focusMainWindow() {
        User32.bringToFront(prevWinAppInfo.value?.threadId, mainHWND)
    }

    override suspend fun hideMainWindowAndPaste(preparePaste: suspend () -> Boolean) {
        logger.info { "unActive main window" }
        bringToBack(preparePaste(), mainHWND)
        hideMainWindow()
    }

    override fun focusSearchWindow() {
        User32.bringToFront(prevWinAppInfo.value?.threadId, searchHWND)
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

data class WinAppInfo(
    val hwnd: HWND,
    val filePath: String,
    val threadId: Int,
)
