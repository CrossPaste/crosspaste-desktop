package com.crosspaste.os.macos

import com.crosspaste.listener.KeyboardKey
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.os.macos.api.MacosApi
import com.sun.jna.Memory

class MacPasteUtils(private val shortcutKeys: ShortcutKeys) {

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

    fun simulatePasteCommand() {
        val (memory, size) = getPasteMemory()
        MacosApi.INSTANCE.simulatePasteCommand(memory, size)
    }
}
