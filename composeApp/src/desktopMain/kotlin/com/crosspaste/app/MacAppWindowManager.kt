package com.crosspaste.app

import com.crosspaste.listen.ActiveGraphicsDevice
import com.crosspaste.listener.KeyboardKey
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.os.macos.api.MacosApi
import com.crosspaste.utils.getSystemProperty
import com.sun.jna.Memory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MacAppWindowManager(
    lazyShortcutKeys: Lazy<ShortcutKeys>,
    private val activeGraphicsDevice: ActiveGraphicsDevice,
) : AbstractAppWindowManager() {

    private val crosspasteBundleID = getSystemProperty().get("mac.bundleID")

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
        val appImagePath = pathProvider.resolve("$localizedName.png", AppFileType.ICON)
        if (!appImagePath.toFile().exists()) {
            MacosApi.INSTANCE.saveAppIcon(bundleIdentifier, appImagePath.toString())
        }
    }

    override suspend fun activeMainWindow() {
        logger.info { "active main window" }
        showMainWindow = true
        MacosApi.INSTANCE.bringToFront(MAIN_WINDOW_TITLE).let {
            createMacAppInfo(it)?.let { macAppInfo ->
                if (macAppInfo.bundleIdentifier != crosspasteBundleID) {
                    prevMacAppInfo = macAppInfo
                    logger.info { "save prevAppName $macAppInfo" }
                }
            }
        }
        delay(500)
        mainFocusRequester.requestFocus()
    }

    override suspend fun unActiveMainWindow() {
        logger.info { "unActive main window" }
        MacosApi.INSTANCE.mainToBack(
            prevMacAppInfo?.bundleIdentifier ?: "",
        )
        showMainWindow = false
        mainFocusRequester.freeFocus()
    }

    override suspend fun activeSearchWindow() {
        logger.info { "active search window" }
        showSearchWindow = true

        activeGraphicsDevice.getGraphicsDevice()?.let { graphicsDevice ->
            searchWindowState.position = calPosition(graphicsDevice.defaultConfiguration.bounds)
        }

        MacosApi.INSTANCE.bringToFront(SEARCH_WINDOW_TITLE).let {
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
        val pair = macPasteUtils.getPasteMemory()
        MacosApi.INSTANCE.searchToBack(
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
        val currentKeys = shortcutKeys.shortcutKeysCore.keys["paste"] ?: emptyList()
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
