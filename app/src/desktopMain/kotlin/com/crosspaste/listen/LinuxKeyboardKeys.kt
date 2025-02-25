package com.crosspaste.listen

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent

// https://gist.github.com/rickyzhang82/8581a762c9f9fc6ddb8390872552c250
object LinuxKeyboardKeys : KeyboardKeys {
    override val ENTER: KeyboardKeyDefine =
        KeyboardKeyDefine("Enter", NativeKeyEvent.VC_ENTER, 108) { it.keyCode == NativeKeyEvent.VC_ENTER }

    override val ESC: KeyboardKeyDefine =
        KeyboardKeyDefine("Esc", NativeKeyEvent.VC_ESCAPE, 9) { it.keyCode == NativeKeyEvent.VC_ESCAPE }

    override val DELETE: KeyboardKeyDefine =
        KeyboardKeyDefine("Delete", NativeKeyEvent.VC_DELETE, 107) { it.keyCode == NativeKeyEvent.VC_DELETE }

    override val BACKSPACE: KeyboardKeyDefine =
        KeyboardKeyDefine("⌫", NativeKeyEvent.VC_BACKSPACE, 22) { it.keyCode == NativeKeyEvent.VC_BACKSPACE }

    override val TAB: KeyboardKeyDefine =
        KeyboardKeyDefine("Tab", NativeKeyEvent.VC_TAB, 23) { it.keyCode == NativeKeyEvent.VC_TAB }

    override val SPACE: KeyboardKeyDefine =
        KeyboardKeyDefine("Space", NativeKeyEvent.VC_SPACE, 65) { it.keyCode == NativeKeyEvent.VC_SPACE }

    override val BACKTICK: KeyboardKeyDefine =
        KeyboardKeyDefine("`", NativeKeyEvent.VC_BACKQUOTE, 49) { it.keyCode == NativeKeyEvent.VC_BACKQUOTE }

    override val UP: KeyboardKeyDefine =
        KeyboardKeyDefine("↑", NativeKeyEvent.VC_UP, 98) { it.keyCode == NativeKeyEvent.VC_UP }

    override val DOWN: KeyboardKeyDefine =
        KeyboardKeyDefine("↓", NativeKeyEvent.VC_DOWN, 104) { it.keyCode == NativeKeyEvent.VC_DOWN }

    override val LEFT: KeyboardKeyDefine =
        KeyboardKeyDefine("←", NativeKeyEvent.VC_LEFT, 100) { it.keyCode == NativeKeyEvent.VC_LEFT }

    override val RIGHT: KeyboardKeyDefine =
        KeyboardKeyDefine("→", NativeKeyEvent.VC_RIGHT, 102) { it.keyCode == NativeKeyEvent.VC_RIGHT }

    override val CTRL: KeyboardKeyDefine =
        KeyboardKeyDefine("Ctrl", NativeKeyEvent.VC_CONTROL, 37) { (it.modifiers and NativeKeyEvent.CTRL_MASK) != 0 }

    override val ALT: KeyboardKeyDefine =
        KeyboardKeyDefine("Alt", NativeKeyEvent.VC_ALT, 64) { (it.modifiers and NativeKeyEvent.ALT_MASK) != 0 }

    override val SHIFT: KeyboardKeyDefine =
        KeyboardKeyDefine("Shift", NativeKeyEvent.VC_SHIFT, 50) { (it.modifiers and NativeKeyEvent.SHIFT_MASK) != 0 }

    override val COMMAND: KeyboardKeyDefine =
        KeyboardKeyDefine("Super", NativeKeyEvent.VC_META, 117) { (it.modifiers and NativeKeyEvent.META_MASK) != 0 }

    override val COMMA: KeyboardKeyDefine =
        KeyboardKeyDefine(",", NativeKeyEvent.VC_COMMA, 59) { it.keyCode == NativeKeyEvent.VC_COMMA }

    override val PERIOD: KeyboardKeyDefine =
        KeyboardKeyDefine(".", NativeKeyEvent.VC_PERIOD, 60) { it.keyCode == NativeKeyEvent.VC_PERIOD }

    override val SLASH: KeyboardKeyDefine =
        KeyboardKeyDefine("/", NativeKeyEvent.VC_SLASH, 61) { it.keyCode == NativeKeyEvent.VC_SLASH }

    override val SEMICOLON: KeyboardKeyDefine =
        KeyboardKeyDefine(";", NativeKeyEvent.VC_SEMICOLON, 47) { it.keyCode == NativeKeyEvent.VC_SEMICOLON }

    override val QUOTE: KeyboardKeyDefine =
        KeyboardKeyDefine("'", NativeKeyEvent.VC_QUOTE, 48) { it.keyCode == NativeKeyEvent.VC_QUOTE }

    override val OPEN_BRACKET: KeyboardKeyDefine =
        KeyboardKeyDefine("[", NativeKeyEvent.VC_OPEN_BRACKET, 34) { it.keyCode == NativeKeyEvent.VC_OPEN_BRACKET }

    override val CLOSE_BRACKET: KeyboardKeyDefine =
        KeyboardKeyDefine("]", NativeKeyEvent.VC_CLOSE_BRACKET, 35) { it.keyCode == NativeKeyEvent.VC_CLOSE_BRACKET }

    override val BACK_SLASH: KeyboardKeyDefine =
        KeyboardKeyDefine("\\", NativeKeyEvent.VC_BACK_SLASH, 51) { it.keyCode == NativeKeyEvent.VC_BACK_SLASH }

    override val EQUALS: KeyboardKeyDefine =
        KeyboardKeyDefine("=", NativeKeyEvent.VC_EQUALS, 21) { it.keyCode == NativeKeyEvent.VC_EQUALS }

    override val MINUS: KeyboardKeyDefine =
        KeyboardKeyDefine("-", NativeKeyEvent.VC_MINUS, 20) { it.keyCode == NativeKeyEvent.VC_MINUS }

    override val F1: KeyboardKeyDefine =
        KeyboardKeyDefine("F1", NativeKeyEvent.VC_F1, 67) { it.keyCode == NativeKeyEvent.VC_F1 }

    override val F2: KeyboardKeyDefine =
        KeyboardKeyDefine("F2", NativeKeyEvent.VC_F2, 68) { it.keyCode == NativeKeyEvent.VC_F2 }

    override val F3: KeyboardKeyDefine =
        KeyboardKeyDefine("F3", NativeKeyEvent.VC_F3, 69) { it.keyCode == NativeKeyEvent.VC_F3 }

    override val F4: KeyboardKeyDefine =
        KeyboardKeyDefine("F4", NativeKeyEvent.VC_F4, 70) { it.keyCode == NativeKeyEvent.VC_F4 }

    override val F5: KeyboardKeyDefine =
        KeyboardKeyDefine("F5", NativeKeyEvent.VC_F5, 71) { it.keyCode == NativeKeyEvent.VC_F5 }

    override val F6: KeyboardKeyDefine =
        KeyboardKeyDefine("F6", NativeKeyEvent.VC_F6, 72) { it.keyCode == NativeKeyEvent.VC_F6 }

    override val F7: KeyboardKeyDefine =
        KeyboardKeyDefine("F7", NativeKeyEvent.VC_F7, 73) { it.keyCode == NativeKeyEvent.VC_F7 }

    override val F8: KeyboardKeyDefine =
        KeyboardKeyDefine("F8", NativeKeyEvent.VC_F8, 74) { it.keyCode == NativeKeyEvent.VC_F8 }

    override val F9: KeyboardKeyDefine =
        KeyboardKeyDefine("F9", NativeKeyEvent.VC_F9, 75) { it.keyCode == NativeKeyEvent.VC_F9 }

    override val F10: KeyboardKeyDefine =
        KeyboardKeyDefine("F10", NativeKeyEvent.VC_F10, 76) { it.keyCode == NativeKeyEvent.VC_F10 }

    override val F11: KeyboardKeyDefine =
        KeyboardKeyDefine("F11", NativeKeyEvent.VC_F11, 95) { it.keyCode == NativeKeyEvent.VC_F11 }

    override val F12: KeyboardKeyDefine =
        KeyboardKeyDefine("F12", NativeKeyEvent.VC_F12, 96) { it.keyCode == NativeKeyEvent.VC_F12 }

    override val _1: KeyboardKeyDefine =
        KeyboardKeyDefine("1", NativeKeyEvent.VC_1, 10) { it.keyCode == NativeKeyEvent.VC_1 }

    override val _2: KeyboardKeyDefine =
        KeyboardKeyDefine("2", NativeKeyEvent.VC_2, 11) { it.keyCode == NativeKeyEvent.VC_2 }

    override val _3: KeyboardKeyDefine =
        KeyboardKeyDefine("3", NativeKeyEvent.VC_3, 12) { it.keyCode == NativeKeyEvent.VC_3 }

    override val _4: KeyboardKeyDefine =
        KeyboardKeyDefine("4", NativeKeyEvent.VC_4, 13) { it.keyCode == NativeKeyEvent.VC_4 }

    override val _5: KeyboardKeyDefine =
        KeyboardKeyDefine("5", NativeKeyEvent.VC_5, 14) { it.keyCode == NativeKeyEvent.VC_5 }

    override val _6: KeyboardKeyDefine =
        KeyboardKeyDefine("6", NativeKeyEvent.VC_6, 15) { it.keyCode == NativeKeyEvent.VC_6 }

    override val _7: KeyboardKeyDefine =
        KeyboardKeyDefine("7", NativeKeyEvent.VC_7, 16) { it.keyCode == NativeKeyEvent.VC_7 }

    override val _8: KeyboardKeyDefine =
        KeyboardKeyDefine("8", NativeKeyEvent.VC_8, 17) { it.keyCode == NativeKeyEvent.VC_8 }

    override val _9: KeyboardKeyDefine =
        KeyboardKeyDefine("9", NativeKeyEvent.VC_9, 18) { it.keyCode == NativeKeyEvent.VC_9 }

    override val _0: KeyboardKeyDefine =
        KeyboardKeyDefine("0", NativeKeyEvent.VC_0, 19) { it.keyCode == NativeKeyEvent.VC_0 }

    override val A: KeyboardKeyDefine =
        KeyboardKeyDefine("A", NativeKeyEvent.VC_A, 38) { it.keyCode == NativeKeyEvent.VC_A }

    override val B: KeyboardKeyDefine =
        KeyboardKeyDefine("B", NativeKeyEvent.VC_B, 56) { it.keyCode == NativeKeyEvent.VC_B }

    override val C: KeyboardKeyDefine =
        KeyboardKeyDefine("C", NativeKeyEvent.VC_C, 54) { it.keyCode == NativeKeyEvent.VC_C }

    override val D: KeyboardKeyDefine =
        KeyboardKeyDefine("D", NativeKeyEvent.VC_D, 40) { it.keyCode == NativeKeyEvent.VC_D }

    override val E: KeyboardKeyDefine =
        KeyboardKeyDefine("E", NativeKeyEvent.VC_E, 26) { it.keyCode == NativeKeyEvent.VC_E }

    override val F: KeyboardKeyDefine =
        KeyboardKeyDefine("F", NativeKeyEvent.VC_F, 41) { it.keyCode == NativeKeyEvent.VC_F }

    override val G: KeyboardKeyDefine =
        KeyboardKeyDefine("G", NativeKeyEvent.VC_G, 42) { it.keyCode == NativeKeyEvent.VC_G }

    override val H: KeyboardKeyDefine =
        KeyboardKeyDefine("H", NativeKeyEvent.VC_H, 43) { it.keyCode == NativeKeyEvent.VC_H }

    override val I: KeyboardKeyDefine =
        KeyboardKeyDefine("I", NativeKeyEvent.VC_I, 31) { it.keyCode == NativeKeyEvent.VC_I }

    override val J: KeyboardKeyDefine =
        KeyboardKeyDefine("J", NativeKeyEvent.VC_J, 44) { it.keyCode == NativeKeyEvent.VC_J }

    override val K: KeyboardKeyDefine =
        KeyboardKeyDefine("K", NativeKeyEvent.VC_K, 45) { it.keyCode == NativeKeyEvent.VC_K }

    override val L: KeyboardKeyDefine =
        KeyboardKeyDefine("L", NativeKeyEvent.VC_L, 46) { it.keyCode == NativeKeyEvent.VC_L }

    override val M: KeyboardKeyDefine =
        KeyboardKeyDefine("M", NativeKeyEvent.VC_M, 58) { it.keyCode == NativeKeyEvent.VC_M }

    override val N: KeyboardKeyDefine =
        KeyboardKeyDefine("N", NativeKeyEvent.VC_N, 57) { it.keyCode == NativeKeyEvent.VC_N }

    override val O: KeyboardKeyDefine =
        KeyboardKeyDefine("O", NativeKeyEvent.VC_O, 32) { it.keyCode == NativeKeyEvent.VC_O }

    override val P: KeyboardKeyDefine =
        KeyboardKeyDefine("P", NativeKeyEvent.VC_P, 33) { it.keyCode == NativeKeyEvent.VC_P }

    override val Q: KeyboardKeyDefine =
        KeyboardKeyDefine("Q", NativeKeyEvent.VC_Q, 24) { it.keyCode == NativeKeyEvent.VC_Q }

    override val R: KeyboardKeyDefine =
        KeyboardKeyDefine("R", NativeKeyEvent.VC_R, 27) { it.keyCode == NativeKeyEvent.VC_R }

    override val S: KeyboardKeyDefine =
        KeyboardKeyDefine("S", NativeKeyEvent.VC_S, 39) { it.keyCode == NativeKeyEvent.VC_S }

    override val T: KeyboardKeyDefine =
        KeyboardKeyDefine("T", NativeKeyEvent.VC_T, 28) { it.keyCode == NativeKeyEvent.VC_T }

    override val U: KeyboardKeyDefine =
        KeyboardKeyDefine("U", NativeKeyEvent.VC_U, 30) { it.keyCode == NativeKeyEvent.VC_U }

    override val V: KeyboardKeyDefine =
        KeyboardKeyDefine("V", NativeKeyEvent.VC_V, 55) { it.keyCode == NativeKeyEvent.VC_V }

    override val W: KeyboardKeyDefine =
        KeyboardKeyDefine("W", NativeKeyEvent.VC_W, 25) { it.keyCode == NativeKeyEvent.VC_W }

    override val X: KeyboardKeyDefine =
        KeyboardKeyDefine("X", NativeKeyEvent.VC_X, 53) { it.keyCode == NativeKeyEvent.VC_X }

    override val Y: KeyboardKeyDefine =
        KeyboardKeyDefine("Y", NativeKeyEvent.VC_Y, 29) { it.keyCode == NativeKeyEvent.VC_Y }

    override val Z: KeyboardKeyDefine =
        KeyboardKeyDefine("Z", NativeKeyEvent.VC_Z, 52) { it.keyCode == NativeKeyEvent.VC_Z }
}
