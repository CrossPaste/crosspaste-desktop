package com.crosspaste.listener

import androidx.compose.ui.util.fastAll
import com.crosspaste.platform.Platform
import com.crosspaste.utils.DesktopResourceUtils
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import okio.Path
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Properties
import kotlin.collections.iterator

class DesktopShortcutKeysLoader(
    platform: Platform,
    private val shortcutKeysAction: ShortcutKeysAction,
) : ShortcutKeysLoader {
    private val keyboardKeys = getDesktopKeyboardKeys(platform)

    private val comparator = keyboardKeys.getComparator()

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

    private fun toConsumer(keys: LinkedHashMap<String, List<KeyboardKey>>): EventConsumer<NativeKeyEvent> {
        val keyboardKeySet: Set<KeyboardKey> = keys.values.flatten().toSet()

        val matchMap: Map<Int, (NativeKeyEvent) -> Boolean> =
            keyboardKeySet.associate { info ->
                info.code to { event ->
                    (
                        map[info.code]?.match?.let { match ->
                            match(event)
                        } == true
                    )
                }
            }

        // The system paste keystroke (e.g. Cmd+V / Ctrl+V) is reserved: CrossPaste
        // simulates it to perform a paste, and that simulated keystroke is seen again
        // by the global listener. If any other action is bound to the same combination
        // (stale config or a hand-edited properties file), firing it would re-simulate
        // the paste and loop forever (issue #4500). Compute those colliding actions once
        // here so dispatch can ignore them without per-event allocation.
        val pasteKeys: List<KeyboardKey> = keys[DesktopShortcutKeys.PASTE] ?: emptyList()
        val reservedConflictKeys: Set<String> =
            if (pasteKeys.isEmpty()) {
                emptySet()
            } else {
                keys
                    .filter { (name, combo) -> name != DesktopShortcutKeys.PASTE && combo.sameShortcutAs(pasteKeys) }
                    .keys
            }

        return EventConsumer { event ->
            for (entry in keys) {
                entry.value
                    .toList()
                    .fastAll { info ->
                        val eventCheck = matchMap[info.code] ?: { false }
                        eventCheck(event)
                    }.let { match ->
                        if (match) {
                            if (entry.key in reservedConflictKeys) {
                                return@EventConsumer
                            }
                            shortcutKeysAction.action(entry.key, event)
                            return@EventConsumer
                        }
                    }
            }
        }
    }

    private fun loadKeys(properties: Properties): LinkedHashMap<String, List<KeyboardKey>> =
        properties
            .map { it.key.toString() to parseKeys(it.value.toString()) }
            .filter { it.second.isNotEmpty() }
            .sortedByDescending { it.second.size }
            .toMap(LinkedHashMap())

    private fun parseKeys(define: String): List<KeyboardKey> =
        define
            .split("+")
            .filter { it != "" }
            .mapNotNull {
                runCatching {
                    val code = it.toInt()
                    map[code]
                }.getOrNull()
            }.sortedWith(comparator)
}
