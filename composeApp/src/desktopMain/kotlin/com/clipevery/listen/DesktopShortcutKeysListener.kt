package com.clipevery.listen

import androidx.compose.runtime.mutableStateListOf
import com.clipevery.listener.KeyboardKeyInfo
import com.clipevery.listener.ShortcutKeys
import com.clipevery.listener.ShortcutKeysListener
import com.clipevery.platform.currentPlatform
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener

class DesktopShortcutKeysListener(
    private val shortcutKeys: ShortcutKeys,
) : ShortcutKeysListener, NativeKeyListener {

    private val platform = currentPlatform()

    private val keyboardKeys =
        if (platform.isMacos()) {
            MacKeyboardKeys()
        } else if (platform.isWindows()) {
            WindowsKeyboardKeys()
        } else if (platform.isLinux()) {
            LinuxKeyboardKeys()
        } else {
            throw IllegalStateException("Unsupported platform: $platform")
        }

    private val groupKeys = keyboardKeys.groupKeys()

    @Volatile
    override var editShortcutKeysMode: Boolean = false

    override var currentKeys: MutableList<KeyboardKeyInfo> = mutableStateListOf()

    override fun nativeKeyPressed(nativeEvent: NativeKeyEvent) {
        if (!editShortcutKeysMode) {
            shortcutKeys.shortcutKeysCore.eventConsumer.accept(nativeEvent)
        } else {
            val list: MutableList<Triple<String, Int, (NativeKeyEvent) -> Boolean>> = mutableListOf()
            val combineKeys = groupKeys[true]!!
            for (value in combineKeys.values) {
                if (value.third(nativeEvent)) {
                    list.add(value)
                }
            }

            val noCombineKeys = groupKeys[false]!!
            noCombineKeys[nativeEvent.keyCode]?.let {
                list.add(it)
            }
            currentKeys.clear()
            currentKeys.addAll(list.map { KeyboardKeyInfo(it.first, it.second) })
        }
    }
}
