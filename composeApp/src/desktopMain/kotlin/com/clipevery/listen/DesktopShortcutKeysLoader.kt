package com.clipevery.listen

import androidx.compose.ui.util.fastAll
import com.clipevery.listener.ShortcutKeysAction
import com.clipevery.listener.ShortcutKeysCore
import com.clipevery.platform.currentPlatform
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import java.util.Properties
import java.util.TreeMap
import java.util.function.Consumer

class DesktopShortcutKeysLoader(
    private val shortcutKeysAction: ShortcutKeysAction,
) : ShortcutKeysLoader {
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

    private val map: Map<String, Triple<String, Int, (NativeKeyEvent) -> Boolean>> =
        keyboardKeys.initializeMap()

    @Suppress("UNCHECKED_CAST")
    override fun load(properties: Properties): ShortcutKeysCore {
        val keys = loadKeys(properties)
        val consumer: Consumer<Any> = toConsumer(keys) as Consumer<Any>
        return ShortcutKeysCore(consumer, keys)
    }

    private fun toConsumer(keys: TreeMap<String, Array<String>>): Consumer<NativeKeyEvent> {
        return Consumer { event ->
            val keyboardKeySet = keys.values.flatMap { it.toSet() }

            val matchMap: Map<String, Boolean> =
                keyboardKeySet.associateWith { key ->
                    (
                        map[key]?.third?.let { match ->
                            match(event)
                        } ?: false
                    )
                }

            for (entry in keys) {
                entry.value.toList().fastAll { key ->
                    matchMap[key] ?: false
                }.let { match ->
                    if (match) {
                        shortcutKeysAction.action(entry.key)
                        return@Consumer
                    }
                }
            }
        }
    }

    private fun loadKeys(properties: Properties): TreeMap<String, Array<String>> {
        return properties.map { it.key.toString() to parseKeys(it.value.toString()) }.toMap(TreeMap())
    }

    private fun parseKeys(define: String): Array<String> {
        val regex = """\s\+\s""".toRegex()
        return regex.split(define).map { it.trim() }.toTypedArray()
    }
}
