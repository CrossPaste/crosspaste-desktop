package com.crosspaste.utils

import com.goncalossilva.murmurhash.MurmurHash3
import okio.Path

expect fun getCodecsUtils(): CodecsUtils

val HEX_DIGITS: CharArray = "0123456789abcdef".toCharArray()

val CROSSPASTE_HASH: MurmurHash3 = MurmurHash3(13043025u)

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

    private fun StringBuilder.appendHex(value: ULong) {
        for (i in 0 until 8) {
            val byte = (value shr i * 8).toByte()
            append(HEX_DIGITS[(byte.toInt() shr 4) and 0xf])
            append(HEX_DIGITS[byte.toInt() and 0xf])
        }
    }

    fun hashByArray(array: Array<String>): String

    fun hashByString(string: String): String

    fun sha256(path: Path): String
}
