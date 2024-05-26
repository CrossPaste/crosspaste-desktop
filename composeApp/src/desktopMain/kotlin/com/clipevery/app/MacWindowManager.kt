package com.clipevery.app

import com.clipevery.listener.KeyboardKey
import com.clipevery.listener.ShortcutKeys
import com.clipevery.os.macos.api.MacosApi
import com.clipevery.path.DesktopPathProvider
import com.clipevery.path.PathProvider
import com.clipevery.utils.getSystemProperty
import com.clipevery.utils.ioDispatcher
import com.sun.jna.Memory
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.io.path.absolutePathString

class MacWindowManager(
    shortcutKeys: ShortcutKeys,
) : WindowManager {

    private val logger = KotlinLogging.logger {}

    private val pathProvider: PathProvider = DesktopPathProvider

    private val ioScope = CoroutineScope(ioDispatcher + SupervisorJob())

    private val clipeveryBundleID = getSystemProperty().get("mac.bundleID")

    private var prevMacAppInfo: MacAppInfo? = null

    private val macPasteUtils: MacPasteUtils = MacPasteUtils(shortcutKeys)

    override fun getPrevAppName(): String? {
        return prevMacAppInfo?.localizedName
    }

    override fun getCurrentActiveAppName(): String? {
        MacosApi.INSTANCE.getCurrentActiveApp()?.let {
            createMacAppInfo(info = it)?.let { macAppInfo ->
                ioScope.launch {
                    saveImagePathByApp(macAppInfo.bundleIdentifier, macAppInfo.localizedName)
                }
                return macAppInfo.localizedName
            }
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

    override suspend fun bringToFront(windowTitle: String) {
        logger.info { "$windowTitle bringToFront Clipevery" }
        MacosApi.INSTANCE.bringToFront(windowTitle).let {
            createMacAppInfo(it)?.let { macAppInfo ->
                if (macAppInfo.bundleIdentifier != clipeveryBundleID) {
                    prevMacAppInfo = macAppInfo
                    logger.info { "save prevAppName $macAppInfo" }
                }
            }
        }
    }

    override suspend fun bringToBack(
        windowTitle: String,
        toPaste: Boolean,
    ) {
        logger.info { "$windowTitle bringToBack Clipevery" }

        val keyCodes = intArrayOf(55, 9)
        val memory = Memory((keyCodes.size * Int.SIZE_BYTES).toLong())
        keyCodes.forEachIndexed { index, value ->
            memory.setInt((index * Int.SIZE_BYTES).toLong(), value)
        }
        val pair = macPasteUtils.getPasteMemory()
        MacosApi.INSTANCE.bringToBack(windowTitle, prevMacAppInfo?.bundleIdentifier ?: "", toPaste, pair.first, pair.second)
    }

    override suspend fun toPaste() {
        val pair = macPasteUtils.getPasteMemory()
        MacosApi.INSTANCE.simulatePasteCommand(pair.first, pair.second)
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
