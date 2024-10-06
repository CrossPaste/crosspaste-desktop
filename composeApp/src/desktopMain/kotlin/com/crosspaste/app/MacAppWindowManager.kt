package com.crosspaste.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.WindowState
import com.crosspaste.listen.ActiveGraphicsDevice
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.macos.MacAppUtils
import com.crosspaste.platform.macos.MacPasteUtils
import com.crosspaste.utils.getSystemProperty
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MacAppWindowManager(
    appSize: AppSize,
    lazyShortcutKeys: Lazy<ShortcutKeys>,
    private val activeGraphicsDevice: ActiveGraphicsDevice,
    private val userDataPathProvider: UserDataPathProvider,
) : DesktopAppWindowManager(appSize) {

    private val crosspasteBundleID = getSystemProperty().get("mac.bundleID")

    private var prevMacAppInfo: MacAppInfo? by mutableStateOf(null)

    private val macPasteUtils: MacPasteUtils by lazy { MacPasteUtils(lazyShortcutKeys.value) }

    override fun getPrevAppName(): String? {
        return prevMacAppInfo?.localizedName
    }

    override fun getCurrentActiveAppName(): String? {
        return try {
            return MacAppUtils.getCurrentActiveApp()?.let {
                createMacAppInfo(info = it)?.let { macAppInfo ->
                    ioScope.launch {
                        saveImagePathByApp(macAppInfo.bundleIdentifier, macAppInfo.localizedName)
                    }
                    macAppInfo.localizedName
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get current active app name" }
            null
        }
    }

    private fun createMacAppInfo(info: String): MacAppInfo? {
        val result = info.split("\n", limit = 2)
        if (result.size == 2) {
            val bundleIdentifier = result[0]
            val localizedName = result[1]
            return MacAppInfo(bundleIdentifier, localizedName)
        }
        return null
    }

    @Synchronized
    private fun saveImagePathByApp(
        bundleIdentifier: String,
        localizedName: String,
    ) {
        val appImagePath = userDataPathProvider.resolve("$localizedName.png", AppFileType.ICON)
        if (!appImagePath.toFile().exists()) {
            MacAppUtils.saveAppIcon(bundleIdentifier, appImagePath.toString())
        }
    }

    override suspend fun activeMainWindow(savePrev: Boolean) {
        logger.info { "active main window" }
        setShowMainWindow(true)
        MacAppUtils.bringToFront(MAIN_WINDOW_TITLE).let {
            if (savePrev) {
                createMacAppInfo(it)?.let { macAppInfo ->
                    if (macAppInfo.bundleIdentifier != crosspasteBundleID) {
                        prevMacAppInfo = macAppInfo
                        logger.info { "save prevAppName $macAppInfo" }
                    }
                }
            }
        }
        delay(500)
        mainFocusRequester.requestFocus()
    }

    override suspend fun unActiveMainWindow(preparePaste: suspend () -> Boolean) {
        logger.info { "unActive main window" }
        val toPaste = preparePaste()
        val prevAppId = prevMacAppInfo?.bundleIdentifier ?: ""
        if (toPaste) {
            val pair = macPasteUtils.getPasteMemory()
            MacAppUtils.mainToBackAndPaste(
                prevAppId,
                pair.first,
                pair.second,
            )
        } else {
            MacAppUtils.mainToBack(prevAppId)
        }
        setShowMainWindow(false)
        delay(500)
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

        MacAppUtils.bringToFront(SEARCH_WINDOW_TITLE).let {
            createMacAppInfo(it)?.let { macAppInfo ->
                if (macAppInfo.bundleIdentifier != crosspasteBundleID) {
                    prevMacAppInfo = macAppInfo
                    logger.info { "save prevAppName $macAppInfo" }
                }
            }
        }

        delay(500)
        searchFocusRequester.requestFocus()
    }

    override suspend fun unActiveSearchWindow(preparePaste: suspend () -> Boolean) {
        logger.info { "unActive search window" }
        val toPaste = preparePaste()
        val prevAppId = prevMacAppInfo?.bundleIdentifier ?: ""
        if (toPaste) {
            val pair = macPasteUtils.getPasteMemory()
            MacAppUtils.searchToBackAndPaste(
                prevAppId,
                pair.first,
                pair.second,
            )
        } else {
            MacAppUtils.searchToBack(prevAppId)
        }
        setShowSearchWindow(false)
        searchFocusRequester.freeFocus()
    }

    override suspend fun toPaste() {
        macPasteUtils.simulatePasteCommand()
    }
}

private data class MacAppInfo(val bundleIdentifier: String, val localizedName: String) {

    override fun toString(): String {
        return "MacAppInfo(bundleIdentifier='$bundleIdentifier', localizedName='$localizedName')"
    }
}
