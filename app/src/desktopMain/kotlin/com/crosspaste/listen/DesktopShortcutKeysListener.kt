package com.crosspaste.listen

import androidx.compose.runtime.mutableStateListOf
import com.crosspaste.listener.KeyboardKey
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.listener.ShortcutKeysListener
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener

class DesktopShortcutKeysListener(
    private val shortcutKeys: ShortcutKeys,
) : ShortcutKeysListener, NativeKeyListener {

    private val keyboardKeys = getDesktopKeyboardKeys()

    private val comparator = keyboardKeys.getComparator()

    private val groupKeys = keyboardKeys.groupModifierKeys

    @Volatile
    override var editShortcutKeysMode: Boolean = false

    override var currentKeys: MutableList<KeyboardKey> = mutableStateListOf()

    override fun nativeKeyPressed(nativeEvent: NativeKeyEvent) {
        if (!editShortcutKeysMode) {
            shortcutKeys.shortcutKeysCore.eventConsumer.accept(nativeEvent)
        } else {
            val list: MutableList<KeyboardKeyDefine> = mutableListOf()
            val combineKeys = groupKeys[true]!!
            for (value in combineKeys.values) {
                if (value.match(nativeEvent)) {
                    list.add(value)
                }
            }

            val noCombineKeys = groupKeys[false]!!
            noCombineKeys[nativeEvent.keyCode]?.let {
                list.add(it)
            }

            currentKeys.clear()
            currentKeys.addAll(list.sortedWith(comparator))
        }
    }
}
