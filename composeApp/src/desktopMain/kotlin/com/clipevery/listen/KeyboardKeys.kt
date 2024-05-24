package com.clipevery.listen

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

interface KeyboardKeys {

    val ENTER: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val ESC: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val DELETE: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val BACKSPACE: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val TAB: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val SPACE: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val UP: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val DOWN: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val LEFT: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val RIGHT: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val CTRL: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val ALT: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val SHIFT: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val COMMAND: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val COMMA: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val PERIOD: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val SLASH: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val SEMICOLON: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val QUOTE: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val OPEN_BRACKET: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val CLOSE_BRACKET: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val BACK_SLASH: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val F1: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val F2: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val F3: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val F4: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val F5: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val F6: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val F7: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val F8: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val F9: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val F10: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val F11: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val F12: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val _1: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val _2: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val _3: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val _4: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val _5: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val _6: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val _7: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val _8: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val _9: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val _0: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val A: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val B: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val C: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val D: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val E: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val F: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val G: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val H: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val I: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val J: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val K: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val L: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val M: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val N: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val O: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val P: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val Q: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val R: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val S: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val T: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val U: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val V: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val W: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val X: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val Y: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    val Z: Triple<String, Int, (NativeKeyEvent) -> Boolean>

    @Suppress("UNCHECKED_CAST")
    fun initializeMap(): Map<String, Triple<String, Int, (NativeKeyEvent) -> Boolean>> {
        val map = mutableMapOf<String, Triple<String, Int, (NativeKeyEvent) -> Boolean>>()
        val clazz = this::class

        clazz.memberProperties.forEach { property ->
            property.isAccessible = true
            if (property.returnType.toString() == "kotlin.Triple<kotlin.String, kotlin.Int, kotlin.Function1<org.jnativehook.keyboard.NativeKeyEvent, kotlin.Boolean>>") {
                val value = property.getter.call(this) as Triple<String, Int, (NativeKeyEvent) -> Boolean>
                map[property.name] = value
            }
        }
        return map
    }
}
