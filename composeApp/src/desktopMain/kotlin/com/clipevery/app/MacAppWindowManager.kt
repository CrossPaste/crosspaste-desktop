package com.clipevery.app

import com.clipevery.listen.ActiveGraphicsDevice
import com.clipevery.listener.KeyboardKey
import com.clipevery.listener.ShortcutKeys
import com.clipevery.os.macos.api.MacosApi
import com.clipevery.utils.getSystemProperty
import com.sun.jna.Memory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.io.path.absolutePathString

class MacAppWindowManager(
    lazyShortcutKeys: Lazy<ShortcutKeys>,
    private val activeGraphicsDevice: ActiveGraphicsDevice,
) : AbstractAppWindowManager() {

    private val clipeveryBundleID = getSystemProperty().get("mac.bundleID")

    private var prevMacAppInfo: MacAppInfo? = null

    private val macPasteUtils: MacPasteUtils by lazy { MacPasteUtils(lazyShortcutKeys.value) }

    override fun getPrevAppName(): String? {
        return prevMacAppInfo?.localizedName
    }

    override fun getCurrentActiveAppName(): String? {
        return try {
            return MacosApi.INSTANCE.getCurrentActiveApp()?.let {
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
        val result = info.split(" ", limit = 2)
        if (result.size > 1) {
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
        val appImagePath = pathProvider.resolve("$localizedName.png", AppFileType.ICON)
        if (!appImagePath.toFile().exists()) {
            MacosApi.INSTANCE.saveAppIcon(bundleIdentifier, appImagePath.absolutePathString())
        }
    }

    override fun activeMainWindow() {
        logger.info { "active main window" }
        showMainWindow = true
        MacosApi.INSTANCE.bringToFront(MAIN_WINDOW_TITLE).let {
            createMacAppInfo(it)?.let { macAppInfo ->
                if (macAppInfo.bundleIdentifier != clipeveryBundleID) {
                    prevMacAppInfo = macAppInfo
                    logger.info { "save prevAppName $macAppInfo" }
                }
            }
        }
    }

    override fun unActiveMainWindow() {
        logger.info { "unActive main window" }
        val pair = macPasteUtils.getPasteMemory()
        MacosApi.INSTANCE.bringToBack(
            MAIN_WINDOW_TITLE,
            prevMacAppInfo?.bundleIdentifier ?: "",
            toPaste = false,
            pair.first,
            pair.second,
        )
        showMainWindow = false
    }

    override suspend fun activeSearchWindow() {
        logger.info { "active search window" }
        showSearchWindow = true

        activeGraphicsDevice.getGraphicsDevice()?.let { graphicsDevice ->
            searchWindowState.position = calPosition(graphicsDevice.defaultConfiguration.bounds)
        }

        MacosApi.INSTANCE.bringToFront(SEARCH_WINDOW_TITLE).let {
            createMacAppInfo(it)?.let { macAppInfo ->
                if (macAppInfo.bundleIdentifier != clipeveryBundleID) {
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
        val pair = macPasteUtils.getPasteMemory()
        MacosApi.INSTANCE.bringToBack(
            SEARCH_WINDOW_TITLE,
            prevMacAppInfo?.bundleIdentifier ?: "",
            toPaste = toPaste,
            pair.first,
            pair.second,
        )
        showSearchWindow = false
        searchFocusRequester.freeFocus()
    }

    override suspend fun toPaste() {
        val pair = macPasteUtils.getPasteMemory()
        MacosApi.INSTANCE.simulatePasteCommand(pair.first, pair.second)
    }
}

private data class MacAppInfo(val bundleIdentifier: String, val localizedName: String) {

    override fun toString(): String {
        return "MacAppInfo(bundleIdentifier='$bundleIdentifier', localizedName='$localizedName')"
    }
}

private class MacPasteUtils(private val shortcutKeys: ShortcutKeys) {

    private var memory: Memory? = null

    private var keys: List<KeyboardKey>? = null

    @Synchronized
    fun getPasteMemory(): Pair<Memory, Int> {
        val currentKeys = shortcutKeys.shortcutKeysCore.keys["Paste"] ?: emptyList()
        if (memory == null || this.keys != currentKeys) {
            this.keys = currentKeys
            memory = createMemory(currentKeys)
        }
        return Pair(memory!!, currentKeys.size)
    }

    private fun createMemory(keys: List<KeyboardKey>): Memory {
        val memorySize = keys.size * Int.SIZE_BYTES
        val newMemory = Memory(memorySize.toLong())

        keys.map { it.rawCode }.toIntArray().let { keyCodes ->
            keyCodes.forEachIndexed { index, value ->
                newMemory.setInt((index * Int.SIZE_BYTES).toLong(), value)
            }
        }
        return newMemory
    }
}
