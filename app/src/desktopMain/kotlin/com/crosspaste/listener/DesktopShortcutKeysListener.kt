package com.crosspaste.listener

import androidx.compose.runtime.mutableStateListOf
import com.crosspaste.platform.Platform
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val RELEASE_DELIVERY_DELAY = 50.milliseconds

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

    private val heldKeyCodes = MutableStateFlow<Set<Int>>(emptySet())

    /**
     * Suspends until no key is held down and the last release has had time to reach
     * the focused window: this hook sees a release before the app it is routed to.
     * [timeout] bounds the wait in case a release event never arrives.
     */
    suspend fun awaitKeysReleased(timeout: Duration = 500.milliseconds) {
        withTimeoutOrNull(timeout) { heldKeyCodes.first { it.isEmpty() } }
        delay(RELEASE_DELIVERY_DELAY)
    }

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
        heldKeyCodes.update { it + nativeEvent.keyCode }
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
        heldKeyCodes.update { it - nativeEvent.keyCode }
        // The injected paste keystroke releasing marks the end of our own simulation:
        // lift suppression now rather than waiting for the safety timeout (issue #4500).
        if (pasteSuppressing) {
            pasteSuppressing = false
        }
    }
}
