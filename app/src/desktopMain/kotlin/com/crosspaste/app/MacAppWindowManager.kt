package com.crosspaste.app

import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.macos.MacAppUtils
import com.crosspaste.platform.macos.MacPasteUtils
import com.crosspaste.utils.getSystemProperty
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MacAppWindowManager(
    appSize: DesktopAppSize,
    lazyShortcutKeys: Lazy<ShortcutKeys>,
    private val userDataPathProvider: UserDataPathProvider,
) : DesktopAppWindowManager(appSize) {

    private val crosspasteBundleID = getSystemProperty().get("mac.bundleID")

    private var prevMacAppInfo: MutableStateFlow<MacAppInfo?> = MutableStateFlow(null)

    private val macPasteUtils: MacPasteUtils by lazy { MacPasteUtils(lazyShortcutKeys.value) }

    override fun getPrevAppName(): Flow<String?> =
        prevMacAppInfo.map { appInfo ->
            appInfo?.localizedName
        }

    override fun getCurrentActiveAppName(): String? =
        runCatching {
            MacAppUtils.getCurrentActiveAppInfo()?.let {
                createMacAppInfo(info = it)?.let { macAppInfo ->
                    ioScope.launch {
                        saveImagePathByApp(macAppInfo.bundleIdentifier, macAppInfo.localizedName)
                    }
                    macAppInfo.localizedName
                }
            }
        }.getOrElse { e ->
            logger.error(e) { "Failed to get current active app name" }
            null
        }

    override fun getRunningAppNames(): List<String> =
        runCatching {
            MacAppUtils
                .getRunningApplications()
                .map { (bundleId, name) ->
                    ioScope.launch { saveImagePathByApp(bundleId, name) }
                    name
                }.distinct()
                .sorted()
        }.getOrElse { e ->
            logger.error(e) { "Failed to get running applications" }
            emptyList()
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

    override fun startWindowService() {
        // do nothing
    }

    override fun stopWindowService() {
        // do nothing
    }

    override fun saveCurrentActiveAppInfo() {
        MacAppUtils.getCurrentActiveAppInfo()?.let {
            createMacAppInfo(it)?.let { macAppInfo ->
                if (macAppInfo.bundleIdentifier != crosspasteBundleID) {
                    prevMacAppInfo.value = macAppInfo
                    logger.info { "save prevAppName $macAppInfo" }
                }
            }
        }
    }

    override suspend fun focusMainWindow(windowTrigger: WindowTrigger) {
        MacAppUtils.bringToFront(mainWindowTitle)
    }

    override suspend fun hideMainWindowAndPaste(preparePaste: suspend () -> Boolean) {
        logger.info { "unActive main window" }
        val toPaste = preparePaste()
        val prevAppId = prevMacAppInfo.value?.bundleIdentifier ?: ""
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
        hideMainWindow()
    }

    override suspend fun focusSearchWindow(windowTrigger: WindowTrigger) {
        MacAppUtils.bringToFront(searchWindowTitle)
    }

    override suspend fun focusBubbleWindow() {
        MacAppUtils.bringToFront(bubbleWindowTitle)
    }

    override suspend fun hideSearchWindowAndPaste(
        size: Int,
        preparePaste: suspend (Int) -> Boolean,
    ) {
        logger.info { "unActive search window" }
        val toPaste = preparePaste(0)
        val prevAppId = prevMacAppInfo.value?.bundleIdentifier ?: ""
        if (toPaste) {
            val pair = macPasteUtils.getPasteMemory()
            MacAppUtils.searchToBackAndPaste(
                prevAppId,
                pair.first,
                pair.second,
            )
            for (i in 1 until size) {
                delay(1000)
                if (preparePaste(i)) {
                    toPaste()
                }
            }
        } else {
            MacAppUtils.searchToBack(prevAppId)
        }
        hideSearchWindow()
    }

    override suspend fun toPaste() {
        macPasteUtils.simulatePasteCommand()
    }
}

private data class MacAppInfo(
    val bundleIdentifier: String,
    val localizedName: String,
) {

    override fun toString(): String = "MacAppInfo(bundleIdentifier='$bundleIdentifier', localizedName='$localizedName')"
}
