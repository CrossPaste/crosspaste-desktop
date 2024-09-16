package com.crosspaste.utils

import okio.Path

expect fun getCodecsUtils(): CodecsUtils

val HEX_DIGITS: CharArray = "0123456789abcdef".toCharArray()

val CROSS_PASTE_SEED = 13043025u

val CROSSPASTE_HASH = MurmurHash3(CROSS_PASTE_SEED)

interface CodecsUtils {

    fun base64Encode(bytes: ByteArray): String

    fun base64Decode(string: String): ByteArray

    fun base64mimeEncode(bytes: ByteArray): String

    fun base64mimeDecode(string: String): ByteArray

    fun hash(bytes: ByteArray): String {
        val (hash1, hash2) = CROSSPASTE_HASH.hash128x64(bytes)
        return buildString(32) {
            appendHex(hash1)
            appendHex(hash2)
        }
    }

    fun hash(path: Path): String

    fun hashByString(string: String): String {
        return hash(string.toByteArray())
    }

    fun hashByArray(array: Array<String>): String

    fun sha256(path: Path): String

    fun StringBuilder.appendHex(value: ULong) {
        for (i in 0 until 8) {
            val byte = (value shr i * 8).toByte()
            append(HEX_DIGITS[(byte.toInt() shr 4) and 0xf])
            append(HEX_DIGITS[byte.toInt() and 0xf])
        }
    }
}
