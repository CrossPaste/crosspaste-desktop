package com.crosspaste.app

import androidx.compose.ui.awt.ComposeWindow
import com.crosspaste.listener.DesktopShortcutKeys.Companion.PASTE
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.listener.ShortcutKeysAction
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.linux.api.X11Api
import com.crosspaste.platform.linux.api.X11Api.Companion.bringToBack
import com.sun.jna.NativeLong
import com.sun.jna.platform.unix.X11.Window
import io.ktor.util.collections.ConcurrentSet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class LinuxAppWindowManager(
    appSize: DesktopAppSize,
    private val lazyShortcutKeys: Lazy<ShortcutKeys>,
    private val lazyShortcutKeysAction: Lazy<ShortcutKeysAction>,
    private val userDataPathProvider: UserDataPathProvider,
) : DesktopAppWindowManager(appSize) {

    private val prevLinuxAppInfo: MutableStateFlow<LinuxAppInfo?> = MutableStateFlow(null)

    private val classNameSet: MutableSet<String> = ConcurrentSet()

    private var _cachedMainWindow: Window? = null
    private var _cachedSearchWindow: Window? = null
    private var _cachedBubbleWindow: Window? = null

    val mainWindow: Window?
        get() {
            if (_cachedMainWindow == null) {
                _cachedMainWindow = X11Api.getWindow(mainWindowTitle)
            }
            return _cachedMainWindow
        }

    val searchWindow: Window?
        get() {
            if (_cachedSearchWindow == null) {
                _cachedSearchWindow = X11Api.getWindow(searchWindowTitle)
            }
            return _cachedSearchWindow
        }

    val bubbleWindow: Window?
        get() {
            if (_cachedBubbleWindow == null) {
                _cachedBubbleWindow = X11Api.getWindow(bubbleWindowTitle)
            }
            return _cachedBubbleWindow
        }

    override fun getCurrentActiveAppName(): String? =
        X11Api.getActiveWindow()?.let { linuxAppInfo ->
            getAppName(linuxAppInfo)
        }

    override fun getRunningAppNames(): List<String> =
        runCatching {
            X11Api
                .getRunningWindows()
                .map { linuxAppInfo ->
                    getAppName(linuxAppInfo)
                }.distinct()
                .sorted()
        }.getOrElse { e ->
            logger.error(e) { "Failed to get running applications" }
            emptyList()
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

    override fun startWindowService() {
        // do nothing
    }

    override fun stopWindowService() {
        // do nothing
    }

    override fun saveCurrentActiveAppInfo() {
        prevLinuxAppInfo.value = X11Api.getActiveWindow()
    }

    override suspend fun focusMainWindow(windowTrigger: WindowTrigger) {
        if (windowTrigger == WindowTrigger.SHORTCUT) {
            val xServerTime = lazyShortcutKeysAction.value.event?.`when`
            X11Api.bringToFront(mainWindow, source = NativeLong(2), xServerTime?.let { NativeLong(it) })
        } else {
            X11Api.bringToFront(mainWindow, source = NativeLong(1))
        }
    }

    override suspend fun hideMainWindowAndPaste(preparePaste: suspend () -> Boolean) {
        logger.info { "unActive main window" }
        bringToBack(preparePaste())
        hideMainWindow()
    }

    override suspend fun focusSearchWindow(windowTrigger: WindowTrigger) {
        if (windowTrigger == WindowTrigger.SHORTCUT) {
            val xServerTime = lazyShortcutKeysAction.value.event?.`when`
            X11Api.bringToFront(searchWindow, source = NativeLong(2), xServerTime?.let { NativeLong(it) })
        } else {
            X11Api.bringToFront(searchWindow, source = NativeLong(1))
        }
    }

    override suspend fun focusBubbleWindow() {
        X11Api.bringToFront(bubbleWindow, source = NativeLong(1))
    }

    override suspend fun hideSearchWindowAndPaste(
        size: Int,
        preparePaste: suspend (Int) -> Boolean,
    ) {
        logger.info { "unActive search window" }
        bringToBack(preparePaste(0))
        for (i in 1 until size) {
            delay(1000)
            if (preparePaste(i)) {
                toPaste()
            }
        }
        hideSearchWindow()
    }

    private suspend fun bringToBack(toPaste: Boolean) {
        if (toPaste) {
            val keyCodes =
                lazyShortcutKeys.value.shortcutKeysCore.value.keys[PASTE]?.let {
                    it.map { key -> key.rawCode }
                } ?: listOf()
            bringToBack(prevLinuxAppInfo.value, keyCodes)
        } else {
            bringToBack(prevLinuxAppInfo.value)
        }
    }

    override suspend fun toPaste() {
        val keyCodes =
            lazyShortcutKeys.value.shortcutKeysCore.value.keys[PASTE]?.let {
                it.map { key -> key.rawCode }
            } ?: listOf()
        X11Api.toPaste(keyCodes)
    }

    override fun onMainComposeWindowChanged(window: ComposeWindow?) {
        logger.debug { "Main ComposeWindow changed (Linux), invalidating X11 Window cache" }
        _cachedMainWindow = null
    }

    override fun onSearchComposeWindowChanged(window: ComposeWindow?) {
        logger.debug { "Search ComposeWindow changed (Linux), invalidating X11 Window cache" }
        _cachedSearchWindow = null
    }

    override fun onBubbleComposeWindowChanged(window: ComposeWindow?) {
        logger.debug { "Bubble ComposeWindow changed (Linux), invalidating X11 Window cache" }
        _cachedBubbleWindow = null
    }
}

data class LinuxAppInfo(
    val window: Window,
    val className: String,
) {

    override fun toString(): String = "LinuxAppInfo(window=$window, className='$className')"
}
