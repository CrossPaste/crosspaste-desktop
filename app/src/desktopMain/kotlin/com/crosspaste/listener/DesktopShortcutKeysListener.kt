package com.crosspaste.listener

import androidx.compose.runtime.mutableStateListOf
import com.crosspaste.platform.Platform
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import kotlin.time.Duration

class DesktopShortcutKeysListener(
    platform: Platform,
    private val shortcutKeys: ShortcutKeys,
) : ShortcutKeysListener,
    NativeKeyListener {

    private val keyboardKeys = getDesktopKeyboardKeys(platform)

    private val comparator = keyboardKeys.getComparator()

    private val groupKeys = keyboardKeys.groupModifierKeys

    @Volatile
    override var editShortcutKeysMode: Boolean = false

    @Volatile
    private var pasteSuppressing: Boolean = false

    @Volatile
    private var pasteSuppressDeadlineMillis: Long = 0L

    override var currentKeys: MutableList<KeyboardKey> = mutableStateListOf()

    override fun beginPasteSuppression(timeout: Duration) {
        pasteSuppressDeadlineMillis = nowEpochMilliseconds() + timeout.inWholeMilliseconds
        pasteSuppressing = true
    }

    private fun isPasteSuppressed(): Boolean {
        if (!pasteSuppressing) {
            return false
        }
        // Safety net: stop suppressing if the injected key-release echo never arrived.
        if (pasteSuppressDeadlineMillis - nowEpochMilliseconds() <= 0) {
            pasteSuppressing = false
            return false
        }
        return true
    }

    override fun nativeKeyPressed(nativeEvent: NativeKeyEvent) {
        if (!editShortcutKeysMode) {
            // Drop events while suppressed: this is CrossPaste's own injected paste
            // keystroke coming back, which would otherwise loop (issue #4500).
            if (isPasteSuppressed()) {
                return
            }
            shortcutKeys.shortcutKeysCore.value.eventConsumer
                .accept(nativeEvent)
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

    override fun nativeKeyReleased(nativeEvent: NativeKeyEvent) {
        // The injected paste keystroke releasing marks the end of our own simulation:
        // lift suppression now rather than waiting for the safety timeout (issue #4500).
        if (pasteSuppressing) {
            pasteSuppressing = false
        }
    }
}
