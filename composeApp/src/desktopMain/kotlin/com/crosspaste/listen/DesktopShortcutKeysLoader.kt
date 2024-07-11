package com.crosspaste.listen

import androidx.compose.ui.util.fastAll
import com.crosspaste.listener.EventConsumer
import com.crosspaste.listener.KeyboardKey
import com.crosspaste.listener.ShortcutKeysAction
import com.crosspaste.listener.ShortcutKeysCore
import com.crosspaste.utils.DesktopResourceUtils
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import okio.Path
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Properties
import java.util.TreeMap

class DesktopShortcutKeysLoader(
    private val shortcutKeysAction: ShortcutKeysAction,
) : ShortcutKeysLoader {
    private val keyboardKeys = getDesktopKeyboardKeys()

    private val map: Map<Int, KeyboardKeyDefine> =
        keyboardKeys.allKeys

    @Suppress("UNCHECKED_CAST")
    override fun load(platformName: String): ShortcutKeysCore {
        val properties = DesktopResourceUtils.loadProperties("shortcut_keys/$platformName.properties")
        val keys = loadKeys(properties)
        val consumer: EventConsumer<Any> = toConsumer(keys) as EventConsumer<Any>
        return ShortcutKeysCore(consumer, keys)
    }

    @Suppress("UNCHECKED_CAST")
    override fun load(path: Path): ShortcutKeysCore {
        val properties = Properties()
        InputStreamReader(path.toFile().inputStream(), StandardCharsets.UTF_8).use { inputStreamReader ->
            properties.load(inputStreamReader)
        }
        val keys = loadKeys(properties)
        val consumer: EventConsumer<Any> = toConsumer(keys) as EventConsumer<Any>
        return ShortcutKeysCore(consumer, keys)
    }

    private fun toConsumer(keys: TreeMap<String, List<KeyboardKey>>): EventConsumer<NativeKeyEvent> {
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

        return EventConsumer { event ->
            for (entry in keys) {
                entry.value.toList().fastAll { info ->
                    val eventCheck = matchMap[info.code] ?: { false }
                    eventCheck(event)
                }.let { match ->
                    if (match) {
                        shortcutKeysAction.action(entry.key)
                        return@EventConsumer
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
