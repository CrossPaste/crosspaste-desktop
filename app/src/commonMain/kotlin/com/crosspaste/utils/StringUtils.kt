package com.crosspaste.utils

fun StringBuilder.appendHex(value: ULong) {
    for (i in 0 until 8) {
        val byte = (value shr i * 8).toByte()
        append(HEX_DIGITS[(byte.toInt() shr 4) and 0xf])
        append(HEX_DIGITS[byte.toInt() and 0xf])
    }
}
