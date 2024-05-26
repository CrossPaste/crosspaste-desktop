package com.clipevery.listen

import kotlin.reflect.full.memberProperties

interface KeyboardKeys {

    val ENTER: KeyboardKeyDefine

    val ESC: KeyboardKeyDefine

    val DELETE: KeyboardKeyDefine

    val BACKSPACE: KeyboardKeyDefine

    val TAB: KeyboardKeyDefine

    val SPACE: KeyboardKeyDefine

    val UP: KeyboardKeyDefine

    val DOWN: KeyboardKeyDefine

    val LEFT: KeyboardKeyDefine

    val RIGHT: KeyboardKeyDefine

    val CTRL: KeyboardKeyDefine

    val ALT: KeyboardKeyDefine

    val SHIFT: KeyboardKeyDefine

    val COMMAND: KeyboardKeyDefine

    val COMMA: KeyboardKeyDefine

    val PERIOD: KeyboardKeyDefine

    val SLASH: KeyboardKeyDefine

    val SEMICOLON: KeyboardKeyDefine

    val QUOTE: KeyboardKeyDefine

    val OPEN_BRACKET: KeyboardKeyDefine

    val CLOSE_BRACKET: KeyboardKeyDefine

    val BACK_SLASH: KeyboardKeyDefine

    val EQUALS: KeyboardKeyDefine

    val MINUS: KeyboardKeyDefine

    val F1: KeyboardKeyDefine

    val F2: KeyboardKeyDefine

    val F3: KeyboardKeyDefine

    val F4: KeyboardKeyDefine

    val F5: KeyboardKeyDefine

    val F6: KeyboardKeyDefine

    val F7: KeyboardKeyDefine

    val F8: KeyboardKeyDefine

    val F9: KeyboardKeyDefine

    val F10: KeyboardKeyDefine

    val F11: KeyboardKeyDefine

    val F12: KeyboardKeyDefine

    val _1: KeyboardKeyDefine

    val _2: KeyboardKeyDefine

    val _3: KeyboardKeyDefine

    val _4: KeyboardKeyDefine

    val _5: KeyboardKeyDefine

    val _6: KeyboardKeyDefine

    val _7: KeyboardKeyDefine

    val _8: KeyboardKeyDefine

    val _9: KeyboardKeyDefine

    val _0: KeyboardKeyDefine

    val A: KeyboardKeyDefine

    val B: KeyboardKeyDefine

    val C: KeyboardKeyDefine

    val D: KeyboardKeyDefine

    val E: KeyboardKeyDefine

    val F: KeyboardKeyDefine

    val G: KeyboardKeyDefine

    val H: KeyboardKeyDefine

    val I: KeyboardKeyDefine

    val J: KeyboardKeyDefine

    val K: KeyboardKeyDefine

    val L: KeyboardKeyDefine

    val M: KeyboardKeyDefine

    val N: KeyboardKeyDefine

    val O: KeyboardKeyDefine

    val P: KeyboardKeyDefine

    val Q: KeyboardKeyDefine

    val R: KeyboardKeyDefine

    val S: KeyboardKeyDefine

    val T: KeyboardKeyDefine

    val U: KeyboardKeyDefine

    val V: KeyboardKeyDefine

    val W: KeyboardKeyDefine

    val X: KeyboardKeyDefine

    val Y: KeyboardKeyDefine

    val Z: KeyboardKeyDefine

    val allKeys: Map<Int, KeyboardKeyDefine>
        get() = initAllMap()

    val groupModifierKeys: Map<Boolean, Map<Int, KeyboardKeyDefine>>
        get() = initGroupModifierKeys()

    fun initAllMap(): Map<Int, KeyboardKeyDefine> {
        return this::class.memberProperties
            .filter { it.returnType.toString() == "com.clipevery.listen.KeyboardKeyDefine" }
            .map { it.getter.call(this) as KeyboardKeyDefine }
            .associateBy { it.code }
    }

    fun initGroupModifierKeys(): Map<Boolean, Map<Int, KeyboardKeyDefine>> {
        val map: Map<Int, KeyboardKeyDefine> = initAllMap()
        val combinationKeys: (Int) -> Boolean = { it == SHIFT.code || it == CTRL.code || it == ALT.code || it == COMMAND.code }
        return map.values.groupBy { combinationKeys(it.code) }
            .map { it.key to it.value.associateBy { info -> info.code } }
            .toMap()
    }
}
