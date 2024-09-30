package com.crosspaste.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.WindowState
import com.crosspaste.listen.ActiveGraphicsDevice
import com.crosspaste.listen.DesktopShortcutKeys.Companion.PASTE
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.linux.api.X11Api
import com.sun.jna.platform.unix.X11.Window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LinuxAppWindowManager(
    appSize: AppSize,
    private val lazyShortcutKeys: Lazy<ShortcutKeys>,
    private val activeGraphicsDevice: ActiveGraphicsDevice,
    private val userDataPathProvider: UserDataPathProvider,
) : DesktopAppWindowManager(appSize) {

    private var prevLinuxAppInfo: LinuxAppInfo? by mutableStateOf(null)

    private val classNameSet: MutableSet<String> = mutableSetOf()

    override fun getCurrentActiveAppName(): String? {
        return X11Api.getActiveWindow()?.let { linuxAppInfo ->
            getAppName(linuxAppInfo)
        }
    }

    override fun getPrevAppName(): String? {
        return prevLinuxAppInfo?.let {
            getAppName(it)
        }
    }

    private fun getAppName(linuxAppInfo: LinuxAppInfo): String {
        val className = linuxAppInfo.className
        if (!classNameSet.contains(className)) {
            ioScope.launch {
                saveAppImage(linuxAppInfo.window, className)
            }
            classNameSet.add(className)
        }
        return className
    }

    @Synchronized
    private fun saveAppImage(
        window: Window,
        className: String,
    ) {
        val iconPath = userDataPathProvider.resolve("$className.png", AppFileType.ICON)
        if (!iconPath.toFile().exists()) {
            X11Api.saveAppIcon(window, iconPath.toNioPath())
        }
    }

    override suspend fun activeMainWindow() {
        logger.info { "active main window" }
        setShowMainWindow(true)
        prevLinuxAppInfo = X11Api.bringToFront(MAIN_WINDOW_TITLE)
        delay(500)
        mainFocusRequester.requestFocus()
    }

    override suspend fun unActiveMainWindow() {
        logger.info { "unActive main window" }
        X11Api.bringToBack(prevLinuxAppInfo)
        setShowMainWindow(false)
        mainFocusRequester.freeFocus()
    }

    override suspend fun activeSearchWindow() {
        logger.info { "active search window" }
        setShowSearchWindow(true)

        activeGraphicsDevice.getGraphicsDevice()?.let { graphicsDevice ->
            setSearchWindowState(
                WindowState(
                    size = appSize.searchWindowSize,
                    position = calPosition(graphicsDevice.defaultConfiguration.bounds),
                ),
            )
        }

        prevLinuxAppInfo = X11Api.bringToFront(SEARCH_WINDOW_TITLE)

        delay(500)
        searchFocusRequester.requestFocus()
    }

    override suspend fun unActiveSearchWindow(preparePaste: suspend () -> Boolean) {
        logger.info { "unActive search window" }
        val toPaste = preparePaste()
        val keyCodes =
            lazyShortcutKeys.value.shortcutKeysCore.keys[PASTE]?.let {
                it.map { key -> key.rawCode }
            } ?: listOf()
        X11Api.bringToBack(prevLinuxAppInfo, toPaste, keyCodes)
        setShowSearchWindow(false)
        searchFocusRequester.freeFocus()
    }

    override suspend fun toPaste() {
        val keyCodes =
            lazyShortcutKeys.value.shortcutKeysCore.keys[PASTE]?.let {
                it.map { key -> key.rawCode }
            } ?: listOf()
        X11Api.toPaste(keyCodes)
    }
}

data class LinuxAppInfo(val window: Window, val className: String) {

    override fun toString(): String {
        return "LinuxAppInfo(window=$window, className='$className')"
    }
}
