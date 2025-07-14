package com.crosspaste.app

import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.listen.DesktopShortcutKeys.Companion.PASTE
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.linux.api.X11Api
import com.sun.jna.platform.unix.X11.Window
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class LinuxAppWindowManager(
    appSize: DesktopAppSize,
    configManager: DesktopConfigManager,
    private val lazyShortcutKeys: Lazy<ShortcutKeys>,
    private val userDataPathProvider: UserDataPathProvider,
) : DesktopAppWindowManager(appSize, configManager) {

    private val prevLinuxAppInfo: MutableStateFlow<LinuxAppInfo?> = MutableStateFlow(null)

    private val classNameSet: MutableSet<String> = mutableSetOf()

    val mainWindow: Window? by lazy {
        X11Api.getWindow(mainWindowTitle)
    }

    val searchWindow: Window? by lazy {
        X11Api.getWindow(searchWindowTitle)
    }

    override fun getCurrentActiveAppName(): String? =
        X11Api.getActiveWindow()?.let { linuxAppInfo ->
            getAppName(linuxAppInfo)
        }

    override fun getPrevAppName(): Flow<String?> =
        prevLinuxAppInfo.map { appInfo ->
            appInfo?.let {
                getAppName(appInfo)
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

    override suspend fun recordActiveInfoAndShowMainWindow(useShortcutKeys: Boolean) {
        logger.info { "active main window" }
        showMainWindow()
        prevLinuxAppInfo.value = X11Api.bringToFront(mainWindow)
    }

    override suspend fun hideMainWindowAndPaste(preparePaste: suspend () -> Boolean) {
        logger.info { "unActive main window" }
        bringToBack(preparePaste())
        hideMainWindow()
    }

    override suspend fun recordActiveInfoAndShowSearchWindow(useShortcutKeys: Boolean) {
        logger.info { "active search window" }

        showSearchWindow()

        setSearchWindowState(appSize.getSearchWindowState())

        prevLinuxAppInfo.value = X11Api.bringToFront(searchWindow)
    }

    override suspend fun hideSearchWindowAndPaste(preparePaste: suspend () -> Boolean) {
        logger.info { "unActive search window" }
        bringToBack(preparePaste())
        hideSearchWindow()
    }

    private suspend fun bringToBack(toPaste: Boolean) {
        if (toPaste) {
            val keyCodes =
                lazyShortcutKeys.value.shortcutKeysCore.value.keys[PASTE]?.let {
                    it.map { key -> key.rawCode }
                } ?: listOf()
            X11Api.bringToBack(prevLinuxAppInfo.value, keyCodes)
        } else {
            X11Api.bringToBack(prevLinuxAppInfo.value)
        }
    }

    override suspend fun toPaste() {
        val keyCodes =
            lazyShortcutKeys.value.shortcutKeysCore.value.keys[PASTE]?.let {
                it.map { key -> key.rawCode }
            } ?: listOf()
        X11Api.toPaste(keyCodes)
    }
}

data class LinuxAppInfo(
    val window: Window,
    val className: String,
) {

    override fun toString(): String = "LinuxAppInfo(window=$window, className='$className')"
}
