package com.crosspaste.listen

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent

// https://gist.github.com/eegrok/949034
object MacKeyboardKeys : KeyboardKeys {

    override val ENTER: KeyboardKeyDefine =
        KeyboardKeyDefine("⏎", NativeKeyEvent.VC_ENTER, 0x34) { it.keyCode == NativeKeyEvent.VC_ENTER }

    override val ESC: KeyboardKeyDefine =
        KeyboardKeyDefine("⎋", NativeKeyEvent.VC_ESCAPE, 0x35) { it.keyCode == NativeKeyEvent.VC_ESCAPE }

    override val DELETE: KeyboardKeyDefine =
        KeyboardKeyDefine("Delete", NativeKeyEvent.VC_DELETE, 0x75) { it.keyCode == NativeKeyEvent.VC_DELETE }

    override val BACKSPACE: KeyboardKeyDefine =
        KeyboardKeyDefine("⌫", NativeKeyEvent.VC_BACKSPACE, 0x33) { it.keyCode == NativeKeyEvent.VC_BACKSPACE }

    override val TAB: KeyboardKeyDefine =
        KeyboardKeyDefine("⇥", NativeKeyEvent.VC_TAB, 0x30) { it.keyCode == NativeKeyEvent.VC_TAB }

    override val SPACE: KeyboardKeyDefine =
        KeyboardKeyDefine("Space", NativeKeyEvent.VC_SPACE, 0x31) { it.keyCode == NativeKeyEvent.VC_SPACE }

    override val UP: KeyboardKeyDefine =
        KeyboardKeyDefine("↑", NativeKeyEvent.VC_UP, 0x7E) { it.keyCode == NativeKeyEvent.VC_UP }

    override val DOWN: KeyboardKeyDefine =
        KeyboardKeyDefine("↓", NativeKeyEvent.VC_DOWN, 0x7D) { it.keyCode == NativeKeyEvent.VC_DOWN }

    override val LEFT: KeyboardKeyDefine =
        KeyboardKeyDefine("←", NativeKeyEvent.VC_LEFT, 0x7B) { it.keyCode == NativeKeyEvent.VC_LEFT }

    override val RIGHT: KeyboardKeyDefine =
        KeyboardKeyDefine("→", NativeKeyEvent.VC_RIGHT, 0x7C) { it.keyCode == NativeKeyEvent.VC_RIGHT }

    override val CTRL: KeyboardKeyDefine =
        KeyboardKeyDefine("⌃", NativeKeyEvent.VC_CONTROL, 0x3B) { (it.modifiers and NativeKeyEvent.CTRL_MASK) != 0 }

    override val ALT: KeyboardKeyDefine =
        KeyboardKeyDefine("⌥", NativeKeyEvent.VC_ALT, 0x3A) { (it.modifiers and NativeKeyEvent.ALT_MASK) != 0 }

    override val SHIFT: KeyboardKeyDefine =
        KeyboardKeyDefine("⇧", NativeKeyEvent.VC_SHIFT, 0x38) { (it.modifiers and NativeKeyEvent.SHIFT_MASK) != 0 }

    override val COMMAND: KeyboardKeyDefine =
        KeyboardKeyDefine("⌘", NativeKeyEvent.VC_META, 0x37) { (it.modifiers and NativeKeyEvent.META_MASK) != 0 }

    override val COMMA: KeyboardKeyDefine =
        KeyboardKeyDefine(",", NativeKeyEvent.VC_COMMA, 0x2B) { it.keyCode == NativeKeyEvent.VC_COMMA }

    override val PERIOD: KeyboardKeyDefine =
        KeyboardKeyDefine(".", NativeKeyEvent.VC_PERIOD, 0x2F) { it.keyCode == NativeKeyEvent.VC_PERIOD }

    override val SLASH: KeyboardKeyDefine =
        KeyboardKeyDefine("/", NativeKeyEvent.VC_SLASH, 0x2C) { it.keyCode == NativeKeyEvent.VC_SLASH }

    override val SEMICOLON: KeyboardKeyDefine =
        KeyboardKeyDefine(";", NativeKeyEvent.VC_SEMICOLON, 0x29) { it.keyCode == NativeKeyEvent.VC_SEMICOLON }

    override val QUOTE: KeyboardKeyDefine =
        KeyboardKeyDefine("'", NativeKeyEvent.VC_QUOTE, 0x27) { it.keyCode == NativeKeyEvent.VC_QUOTE }

    override val OPEN_BRACKET: KeyboardKeyDefine =
        KeyboardKeyDefine("[", NativeKeyEvent.VC_OPEN_BRACKET, 0x21) { it.keyCode == NativeKeyEvent.VC_OPEN_BRACKET }

    override val CLOSE_BRACKET: KeyboardKeyDefine =
        KeyboardKeyDefine("]", NativeKeyEvent.VC_CLOSE_BRACKET, 0x1E) { it.keyCode == NativeKeyEvent.VC_CLOSE_BRACKET }

    override val BACK_SLASH: KeyboardKeyDefine =
        KeyboardKeyDefine("\\", NativeKeyEvent.VC_BACK_SLASH, 0x2A) { it.keyCode == NativeKeyEvent.VC_BACK_SLASH }

    override val EQUALS: KeyboardKeyDefine =
        KeyboardKeyDefine("=", NativeKeyEvent.VC_EQUALS, 0x18) { it.keyCode == NativeKeyEvent.VC_EQUALS }

    override val MINUS: KeyboardKeyDefine =
        KeyboardKeyDefine("-", NativeKeyEvent.VC_MINUS, 0x1B) { it.keyCode == NativeKeyEvent.VC_MINUS }

    override val F1: KeyboardKeyDefine =
        KeyboardKeyDefine("F1", NativeKeyEvent.VC_F1, 0x7A) { it.keyCode == NativeKeyEvent.VC_F1 }

    override val F2: KeyboardKeyDefine =
        KeyboardKeyDefine("F2", NativeKeyEvent.VC_F2, 0x78) { it.keyCode == NativeKeyEvent.VC_F2 }

    override val F3: KeyboardKeyDefine =
        KeyboardKeyDefine("F3", NativeKeyEvent.VC_F3, 0x63) { it.keyCode == NativeKeyEvent.VC_F3 }

    override val F4: KeyboardKeyDefine =
        KeyboardKeyDefine("F4", NativeKeyEvent.VC_F4, 0x76) { it.keyCode == NativeKeyEvent.VC_F4 }

    override val F5: KeyboardKeyDefine =
        KeyboardKeyDefine("F5", NativeKeyEvent.VC_F5, 0x60) { it.keyCode == NativeKeyEvent.VC_F5 }

    override val F6: KeyboardKeyDefine =
        KeyboardKeyDefine("F6", NativeKeyEvent.VC_F6, 0x61) { it.keyCode == NativeKeyEvent.VC_F6 }

    override val F7: KeyboardKeyDefine =
        KeyboardKeyDefine("F7", NativeKeyEvent.VC_F7, 0x62) { it.keyCode == NativeKeyEvent.VC_F7 }

    override val F8: KeyboardKeyDefine =
        KeyboardKeyDefine("F8", NativeKeyEvent.VC_F8, 0x64) { it.keyCode == NativeKeyEvent.VC_F8 }

    override val F9: KeyboardKeyDefine =
        KeyboardKeyDefine("F9", NativeKeyEvent.VC_F9, 0x65) { it.keyCode == NativeKeyEvent.VC_F9 }

    override val F10: KeyboardKeyDefine =
        KeyboardKeyDefine("F10", NativeKeyEvent.VC_F10, 0x6D) { it.keyCode == NativeKeyEvent.VC_F10 }

    override val F11: KeyboardKeyDefine =
        KeyboardKeyDefine("F11", NativeKeyEvent.VC_F11, 0x67) { it.keyCode == NativeKeyEvent.VC_F11 }

    override val F12: KeyboardKeyDefine =
        KeyboardKeyDefine("F12", NativeKeyEvent.VC_F12, 0x6F) { it.keyCode == NativeKeyEvent.VC_F12 }

    override val _1: KeyboardKeyDefine =
        KeyboardKeyDefine("1", NativeKeyEvent.VC_1, 0x12) { it.keyCode == NativeKeyEvent.VC_1 }

    override val _2: KeyboardKeyDefine =
        KeyboardKeyDefine("2", NativeKeyEvent.VC_2, 0x13) { it.keyCode == NativeKeyEvent.VC_2 }

    override val _3: KeyboardKeyDefine =
        KeyboardKeyDefine("3", NativeKeyEvent.VC_3, 0x14) { it.keyCode == NativeKeyEvent.VC_3 }

    override val _4: KeyboardKeyDefine =
        KeyboardKeyDefine("4", NativeKeyEvent.VC_4, 0x15) { it.keyCode == NativeKeyEvent.VC_4 }

    override val _5: KeyboardKeyDefine =
        KeyboardKeyDefine("5", NativeKeyEvent.VC_5, 0x17) { it.keyCode == NativeKeyEvent.VC_5 }

    override val _6: KeyboardKeyDefine =
        KeyboardKeyDefine("6", NativeKeyEvent.VC_6, 0x16) { it.keyCode == NativeKeyEvent.VC_6 }

    override val _7: KeyboardKeyDefine =
        KeyboardKeyDefine("7", NativeKeyEvent.VC_7, 0x1A) { it.keyCode == NativeKeyEvent.VC_7 }

    override val _8: KeyboardKeyDefine =
        KeyboardKeyDefine("8", NativeKeyEvent.VC_8, 0x1C) { it.keyCode == NativeKeyEvent.VC_8 }

    override val _9: KeyboardKeyDefine =
        KeyboardKeyDefine("9", NativeKeyEvent.VC_9, 0x19) { it.keyCode == NativeKeyEvent.VC_9 }

    override val _0: KeyboardKeyDefine =
        KeyboardKeyDefine("0", NativeKeyEvent.VC_0, 0x1F) { it.keyCode == NativeKeyEvent.VC_0 }

    override val A: KeyboardKeyDefine =
        KeyboardKeyDefine("A", NativeKeyEvent.VC_A, 0x00) { it.keyCode == NativeKeyEvent.VC_A }

    override val B: KeyboardKeyDefine =
        KeyboardKeyDefine("B", NativeKeyEvent.VC_B, 0x0B) { it.keyCode == NativeKeyEvent.VC_B }

    override val C: KeyboardKeyDefine =
        KeyboardKeyDefine("C", NativeKeyEvent.VC_C, 0x08) { it.keyCode == NativeKeyEvent.VC_C }

    override val D: KeyboardKeyDefine =
        KeyboardKeyDefine("D", NativeKeyEvent.VC_D, 0x02) { it.keyCode == NativeKeyEvent.VC_D }

    override val E: KeyboardKeyDefine =
        KeyboardKeyDefine("E", NativeKeyEvent.VC_E, 0x0E) { it.keyCode == NativeKeyEvent.VC_E }

    override val F: KeyboardKeyDefine =
        KeyboardKeyDefine("F", NativeKeyEvent.VC_F, 0x03) { it.keyCode == NativeKeyEvent.VC_F }

    override val G: KeyboardKeyDefine =
        KeyboardKeyDefine("G", NativeKeyEvent.VC_G, 0x05) { it.keyCode == NativeKeyEvent.VC_G }

    override val H: KeyboardKeyDefine =
        KeyboardKeyDefine("H", NativeKeyEvent.VC_H, 0x04) { it.keyCode == NativeKeyEvent.VC_H }

    override val I: KeyboardKeyDefine =
        KeyboardKeyDefine("I", NativeKeyEvent.VC_I, 0x22) { it.keyCode == NativeKeyEvent.VC_I }

    override val J: KeyboardKeyDefine =
        KeyboardKeyDefine("J", NativeKeyEvent.VC_J, 0x26) { it.keyCode == NativeKeyEvent.VC_J }

    override val K: KeyboardKeyDefine =
        KeyboardKeyDefine("K", NativeKeyEvent.VC_K, 0x28) { it.keyCode == NativeKeyEvent.VC_K }

    override val L: KeyboardKeyDefine =
        KeyboardKeyDefine("L", NativeKeyEvent.VC_L, 0x25) { it.keyCode == NativeKeyEvent.VC_L }

    override val M: KeyboardKeyDefine =
        KeyboardKeyDefine("M", NativeKeyEvent.VC_M, 0x2E) { it.keyCode == NativeKeyEvent.VC_M }

    override val N: KeyboardKeyDefine =
        KeyboardKeyDefine("N", NativeKeyEvent.VC_N, 0x2D) { it.keyCode == NativeKeyEvent.VC_N }

    override val O: KeyboardKeyDefine =
        KeyboardKeyDefine("O", NativeKeyEvent.VC_O, 0x1F) { it.keyCode == NativeKeyEvent.VC_O }

    override val P: KeyboardKeyDefine =
        KeyboardKeyDefine("P", NativeKeyEvent.VC_P, 0x23) { it.keyCode == NativeKeyEvent.VC_P }

    override val Q: KeyboardKeyDefine =
        KeyboardKeyDefine("Q", NativeKeyEvent.VC_Q, 0x0C) { it.keyCode == NativeKeyEvent.VC_Q }

    override val R: KeyboardKeyDefine =
        KeyboardKeyDefine("R", NativeKeyEvent.VC_R, 0x0F) { it.keyCode == NativeKeyEvent.VC_R }

    override val S: KeyboardKeyDefine =
        KeyboardKeyDefine("S", NativeKeyEvent.VC_S, 0x01) { it.keyCode == NativeKeyEvent.VC_S }

    override val T: KeyboardKeyDefine =
        KeyboardKeyDefine("T", NativeKeyEvent.VC_T, 0x11) { it.keyCode == NativeKeyEvent.VC_T }

    override val U: KeyboardKeyDefine =
        KeyboardKeyDefine("U", NativeKeyEvent.VC_U, 0x20) { it.keyCode == NativeKeyEvent.VC_U }

    override val V: KeyboardKeyDefine =
        KeyboardKeyDefine("V", NativeKeyEvent.VC_V, 0x09) { it.keyCode == NativeKeyEvent.VC_V }

    override val W: KeyboardKeyDefine =
        KeyboardKeyDefine("W", NativeKeyEvent.VC_W, 0x0D) { it.keyCode == NativeKeyEvent.VC_W }

    override val X: KeyboardKeyDefine =
        KeyboardKeyDefine("X", NativeKeyEvent.VC_X, 0x07) { it.keyCode == NativeKeyEvent.VC_X }

    override val Y: KeyboardKeyDefine =
        KeyboardKeyDefine("Y", NativeKeyEvent.VC_Y, 0x10) { it.keyCode == NativeKeyEvent.VC_Y }

    override val Z: KeyboardKeyDefine =
        KeyboardKeyDefine("Z", NativeKeyEvent.VC_Z, 0x06) { it.keyCode == NativeKeyEvent.VC_Z }
}
