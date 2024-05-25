package com.clipevery.listener

import java.util.function.Consumer

data class ShortcutKeysCore(
    val eventConsumer: Consumer<Any>,
    val keys: Map<String, List<KeyboardKeyInfo>>,
)

data class KeyboardKeyInfo(
    val name: String,
    val code: Int,
) : Comparable<KeyboardKeyInfo> {
    override fun compareTo(other: KeyboardKeyInfo): Int {
        return code.compareTo(other.code)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyboardKeyInfo) return false

        if (name != other.name) return false
        if (code != other.code) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + code
        return result
    }

    override fun toString(): String {
        return "KeyboardKeyInfo(name='$name', code=$code)"
    }
}
