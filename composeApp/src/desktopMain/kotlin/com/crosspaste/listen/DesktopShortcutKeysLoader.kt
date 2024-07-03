package com.crosspaste.listen

import androidx.compose.ui.util.fastAll
import com.crosspaste.listener.KeyboardKey
import com.crosspaste.listener.ShortcutKeysAction
import com.crosspaste.listener.ShortcutKeysCore
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import java.util.Properties
import java.util.TreeMap
import java.util.function.Consumer

class DesktopShortcutKeysLoader(
    private val shortcutKeysAction: ShortcutKeysAction,
) : ShortcutKeysLoader {
    private val keyboardKeys = getDesktopKeyboardKeys()

    private val map: Map<Int, KeyboardKeyDefine> =
        keyboardKeys.allKeys

    @Suppress("UNCHECKED_CAST")
    override fun load(properties: Properties): ShortcutKeysCore {
        val keys = loadKeys(properties)
        val consumer: Consumer<Any> = toConsumer(keys) as Consumer<Any>
        return ShortcutKeysCore(consumer, keys)
    }

    private fun toConsumer(keys: TreeMap<String, List<KeyboardKey>>): Consumer<NativeKeyEvent> {
        val keyboardKeySet: Set<KeyboardKey> = keys.values.flatten().toSet()

        val matchMap: Map<Int, (NativeKeyEvent) -> Boolean> =
            keyboardKeySet.associate { info ->
                info.code to { event ->
                    (
                        map[info.code]?.match?.let { match ->
                            match(event)
                        } ?: false
                    )
                }
            }

        return Consumer { event ->
            for (entry in keys) {
                entry.value.toList().fastAll { info ->
                    val eventCheck = matchMap[info.code] ?: { false }
                    eventCheck(event)
                }.let { match ->
                    if (match) {
                        shortcutKeysAction.action(entry.key)
                        return@Consumer
                    }
                }
            }
        }
    }

    private fun loadKeys(properties: Properties): TreeMap<String, List<KeyboardKey>> {
        return properties.map { it.key.toString() to parseKeys(it.value.toString()) }.toMap(TreeMap())
    }

    private fun parseKeys(define: String): List<KeyboardKey> {
        return define.split("+").mapNotNull {
            val code = it.toInt()
            map[code]
        }
    }
}
