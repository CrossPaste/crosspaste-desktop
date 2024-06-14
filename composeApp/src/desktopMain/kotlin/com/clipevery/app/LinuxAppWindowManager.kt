package com.clipevery.app

import com.clipevery.listen.ActiveGraphicsDevice
import com.clipevery.listener.ShortcutKeys
import com.clipevery.os.linux.api.X11Api
import com.sun.jna.platform.unix.X11.Window
import kotlinx.coroutines.launch

class LinuxAppWindowManager(
    private val lazyShortcutKeys: Lazy<ShortcutKeys>,
    private val activeGraphicsDevice: ActiveGraphicsDevice,
) : AbstractAppWindowManager() {

    private var prevLinuxAppInfo: LinuxAppInfo? = null

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
        val iconPath = pathProvider.resolve("$className.png", AppFileType.ICON)
        if (!iconPath.toFile().exists()) {
            X11Api.saveAppIcon(window, iconPath)
        }
    }

    override fun activeMainWindow() {
        logger.info { "active main window" }
        showMainWindow = true
        prevLinuxAppInfo = X11Api.bringToFront(MAIN_WINDOW_TITLE)
    }

    override fun unActiveMainWindow() {
        logger.info { "unActive main window" }
        X11Api.bringToBack(prevLinuxAppInfo)
        showMainWindow = false
    }

    override suspend fun activeSearchWindow() {
        logger.info { "active search window" }
        showSearchWindow = true

        activeGraphicsDevice.getGraphicsDevice()?.let { graphicsDevice ->
            searchWindowState.position = calPosition(graphicsDevice.defaultConfiguration.bounds)
        }

        prevLinuxAppInfo = X11Api.bringToFront(SEARCH_WINDOW_TITLE)
    }

    override suspend fun unActiveSearchWindow(preparePaste: suspend () -> Boolean) {
        logger.info { "unActive search window" }
        val toPaste = preparePaste()
        val keyCodes =
            lazyShortcutKeys.value.shortcutKeysCore.keys["Paste"]?.let {
                it.map { key -> key.rawCode }
            } ?: listOf()
        X11Api.bringToBack(prevLinuxAppInfo, toPaste, keyCodes)
        showSearchWindow = false
        searchFocusRequester.freeFocus()
    }

    override suspend fun toPaste() {
        val keyCodes =
            lazyShortcutKeys.value.shortcutKeysCore.keys["Paste"]?.let {
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
