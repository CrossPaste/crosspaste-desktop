package com.clipevery.listen

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent

class MacKeyboardKeys : KeyboardKeys {
    override val ENTER: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("⏎", NativeKeyEvent.VC_ENTER) { it.keyCode == NativeKeyEvent.VC_ENTER }

    override val ESC: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("⎋", NativeKeyEvent.VC_ESCAPE) { it.keyCode == NativeKeyEvent.VC_ESCAPE }

    override val DELETE: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("Delete", NativeKeyEvent.VC_DELETE) { it.keyCode == NativeKeyEvent.VC_DELETE }

    override val BACKSPACE: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("⌫", NativeKeyEvent.VC_BACKSPACE) { it.keyCode == NativeKeyEvent.VC_BACKSPACE }

    override val TAB: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("⇥", NativeKeyEvent.VC_TAB) { it.keyCode == NativeKeyEvent.VC_TAB }

    override val SPACE: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("Space", NativeKeyEvent.VC_SPACE) { it.keyCode == NativeKeyEvent.VC_SPACE }

    override val UP: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("↑", NativeKeyEvent.VC_UP) { it.keyCode == NativeKeyEvent.VC_UP }

    override val DOWN: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("↓", NativeKeyEvent.VC_DOWN) { it.keyCode == NativeKeyEvent.VC_DOWN }

    override val LEFT: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("←", NativeKeyEvent.VC_LEFT) { it.keyCode == NativeKeyEvent.VC_LEFT }

    override val RIGHT: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("→", NativeKeyEvent.VC_RIGHT) { it.keyCode == NativeKeyEvent.VC_RIGHT }

    override val CTRL: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("⌃", NativeKeyEvent.VC_CONTROL) { it.keyCode == NativeKeyEvent.VC_CONTROL }

    override val ALT: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("⌥", NativeKeyEvent.VC_ALT) { it.keyCode == NativeKeyEvent.VC_ALT }

    override val SHIFT: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("⇧", NativeKeyEvent.VC_SHIFT) { (it.modifiers and NativeKeyEvent.SHIFT_MASK) != 0 }

    override val COMMAND: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("⌘", NativeKeyEvent.VC_META) { (it.modifiers and NativeKeyEvent.META_MASK) != 0 }

    override val COMMA: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple(",", NativeKeyEvent.VC_COMMA) { it.keyCode == NativeKeyEvent.VC_COMMA }

    override val PERIOD: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple(".", NativeKeyEvent.VC_PERIOD) { it.keyCode == NativeKeyEvent.VC_PERIOD }

    override val SLASH: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("/", NativeKeyEvent.VC_SLASH) { it.keyCode == NativeKeyEvent.VC_SLASH }

    override val SEMICOLON: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple(";", NativeKeyEvent.VC_SEMICOLON) { it.keyCode == NativeKeyEvent.VC_SEMICOLON }

    override val QUOTE: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("'", NativeKeyEvent.VC_QUOTE) { it.keyCode == NativeKeyEvent.VC_QUOTE }

    override val OPEN_BRACKET: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("[", NativeKeyEvent.VC_OPEN_BRACKET) { it.keyCode == NativeKeyEvent.VC_OPEN_BRACKET }

    override val CLOSE_BRACKET: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("]", NativeKeyEvent.VC_CLOSE_BRACKET) { it.keyCode == NativeKeyEvent.VC_CLOSE_BRACKET }

    override val BACK_SLASH: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("\\", NativeKeyEvent.VC_BACK_SLASH) { it.keyCode == NativeKeyEvent.VC_BACK_SLASH }

    override val EQUALS: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("=", NativeKeyEvent.VC_EQUALS) { it.keyCode == NativeKeyEvent.VC_EQUALS }

    override val MINUS: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("-", NativeKeyEvent.VC_MINUS) { it.keyCode == NativeKeyEvent.VC_MINUS }

    override val F1: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("F1", NativeKeyEvent.VC_F1) { it.keyCode == NativeKeyEvent.VC_F1 }

    override val F2: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("F2", NativeKeyEvent.VC_F2) { it.keyCode == NativeKeyEvent.VC_F2 }

    override val F3: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("F3", NativeKeyEvent.VC_F3) { it.keyCode == NativeKeyEvent.VC_F3 }

    override val F4: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("F4", NativeKeyEvent.VC_F4) { it.keyCode == NativeKeyEvent.VC_F4 }

    override val F5: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("F5", NativeKeyEvent.VC_F5) { it.keyCode == NativeKeyEvent.VC_F5 }

    override val F6: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("F6", NativeKeyEvent.VC_F6) { it.keyCode == NativeKeyEvent.VC_F6 }

    override val F7: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("F7", NativeKeyEvent.VC_F7) { it.keyCode == NativeKeyEvent.VC_F7 }

    override val F8: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("F8", NativeKeyEvent.VC_F8) { it.keyCode == NativeKeyEvent.VC_F8 }

    override val F9: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("F9", NativeKeyEvent.VC_F9) { it.keyCode == NativeKeyEvent.VC_F9 }

    override val F10: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("F10", NativeKeyEvent.VC_F10) { it.keyCode == NativeKeyEvent.VC_F10 }

    override val F11: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("F11", NativeKeyEvent.VC_F11) { it.keyCode == NativeKeyEvent.VC_F11 }

    override val F12: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("F12", NativeKeyEvent.VC_F12) { it.keyCode == NativeKeyEvent.VC_F12 }

    override val _1: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("1", NativeKeyEvent.VC_1) { it.keyCode == NativeKeyEvent.VC_1 }

    override val _2: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("2", NativeKeyEvent.VC_2) { it.keyCode == NativeKeyEvent.VC_2 }

    override val _3: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("3", NativeKeyEvent.VC_3) { it.keyCode == NativeKeyEvent.VC_3 }

    override val _4: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("4", NativeKeyEvent.VC_4) { it.keyCode == NativeKeyEvent.VC_4 }

    override val _5: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("5", NativeKeyEvent.VC_5) { it.keyCode == NativeKeyEvent.VC_5 }

    override val _6: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("6", NativeKeyEvent.VC_6) { it.keyCode == NativeKeyEvent.VC_6 }

    override val _7: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("7", NativeKeyEvent.VC_7) { it.keyCode == NativeKeyEvent.VC_7 }

    override val _8: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("8", NativeKeyEvent.VC_8) { it.keyCode == NativeKeyEvent.VC_8 }

    override val _9: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("9", NativeKeyEvent.VC_9) { it.keyCode == NativeKeyEvent.VC_9 }

    override val _0: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("0", NativeKeyEvent.VC_0) { it.keyCode == NativeKeyEvent.VC_0 }

    override val A: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("A", NativeKeyEvent.VC_A) { it.keyCode == NativeKeyEvent.VC_A }

    override val B: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("B", NativeKeyEvent.VC_B) { it.keyCode == NativeKeyEvent.VC_B }

    override val C: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("C", NativeKeyEvent.VC_C) { it.keyCode == NativeKeyEvent.VC_C }

    override val D: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("D", NativeKeyEvent.VC_D) { it.keyCode == NativeKeyEvent.VC_D }

    override val E: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("E", NativeKeyEvent.VC_E) { it.keyCode == NativeKeyEvent.VC_E }

    override val F: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("F", NativeKeyEvent.VC_F) { it.keyCode == NativeKeyEvent.VC_F }

    override val G: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("G", NativeKeyEvent.VC_G) { it.keyCode == NativeKeyEvent.VC_G }

    override val H: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("H", NativeKeyEvent.VC_H) { it.keyCode == NativeKeyEvent.VC_H }

    override val I: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("I", NativeKeyEvent.VC_I) { it.keyCode == NativeKeyEvent.VC_I }

    override val J: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("J", NativeKeyEvent.VC_J) { it.keyCode == NativeKeyEvent.VC_J }

    override val K: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("K", NativeKeyEvent.VC_K) { it.keyCode == NativeKeyEvent.VC_K }

    override val L: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("L", NativeKeyEvent.VC_L) { it.keyCode == NativeKeyEvent.VC_L }

    override val M: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("M", NativeKeyEvent.VC_M) { it.keyCode == NativeKeyEvent.VC_M }

    override val N: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("N", NativeKeyEvent.VC_N) { it.keyCode == NativeKeyEvent.VC_N }

    override val O: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("O", NativeKeyEvent.VC_O) { it.keyCode == NativeKeyEvent.VC_O }

    override val P: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("P", NativeKeyEvent.VC_P) { it.keyCode == NativeKeyEvent.VC_P }

    override val Q: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("Q", NativeKeyEvent.VC_Q) { it.keyCode == NativeKeyEvent.VC_Q }

    override val R: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("R", NativeKeyEvent.VC_R) { it.keyCode == NativeKeyEvent.VC_R }

    override val S: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("S", NativeKeyEvent.VC_S) { it.keyCode == NativeKeyEvent.VC_S }

    override val T: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("T", NativeKeyEvent.VC_T) { it.keyCode == NativeKeyEvent.VC_T }

    override val U: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("U", NativeKeyEvent.VC_U) { it.keyCode == NativeKeyEvent.VC_U }

    override val V: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("V", NativeKeyEvent.VC_V) { it.keyCode == NativeKeyEvent.VC_V }

    override val W: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("W", NativeKeyEvent.VC_W) { it.keyCode == NativeKeyEvent.VC_W }

    override val X: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("X", NativeKeyEvent.VC_X) { it.keyCode == NativeKeyEvent.VC_X }

    override val Y: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("Y", NativeKeyEvent.VC_Y) { it.keyCode == NativeKeyEvent.VC_Y }

    override val Z: Triple<String, Int, (NativeKeyEvent) -> Boolean> =
        Triple("Z", NativeKeyEvent.VC_Z) { it.keyCode == NativeKeyEvent.VC_Z }
}
