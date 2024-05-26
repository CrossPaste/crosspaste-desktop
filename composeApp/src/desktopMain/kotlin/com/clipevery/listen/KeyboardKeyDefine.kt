package com.clipevery.listen

import com.clipevery.listener.KeyboardKey
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent

data class KeyboardKeyDefine(
    override val name: String,
    override val code: Int,
    override val rawCode: Int,
    val match: (NativeKeyEvent) -> Boolean,
) : KeyboardKey {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyboardKeyDefine) return false

        if (name != other.name) return false
        if (code != other.code) return false
        if (rawCode != other.rawCode) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + code
        result = 31 * result + rawCode
        return result
    }

    override fun toString(): String {
        return "KeyboardKeyDefine(name='$name', code=$code, rawCode=$rawCode, match=$match)"
    }
}
