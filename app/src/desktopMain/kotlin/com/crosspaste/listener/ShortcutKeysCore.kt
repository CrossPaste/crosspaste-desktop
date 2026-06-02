package com.crosspaste.listener

data class ShortcutKeysCore(
    val eventConsumer: EventConsumer<Any>,
    val keys: Map<String, List<KeyboardKey>>,
)

interface KeyboardKey {

    val name: String
    val code: Int
    val rawCode: Int
}

/**
 * Whether two shortcut key combinations are identical, regardless of the order the keys
 * are listed in. Combinations are compared by their key codes.
 *
 * Used to detect a binding that collides with the reserved system paste keystroke
 * (Cmd+V / Ctrl+V); reusing it for another action would cause an infinite paste loop
 * (issue #4500).
 */
fun List<KeyboardKey>.sameShortcutAs(other: List<KeyboardKey>): Boolean {
    if (size != other.size) return false
    val codes = map { it.code }.sorted()
    val otherCodes = other.map { it.code }.sorted()
    for (i in codes.indices) {
        if (codes[i] != otherCodes[i]) return false
    }
    return true
}

fun interface EventConsumer<T> {

    fun accept(event: T)
}
