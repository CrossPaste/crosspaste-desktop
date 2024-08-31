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
        val hashBytes = CROSSPASTE_HASH.hash128x64(bytes)
        val result = ByteArray(hashBytes.size * 4)

        hashBytes.forEachIndexed { index, uLong ->
            for (i in 0 until 4) {
                val byte = (uLong shr (i * 8)).toByte()
                result[index * 4 + i] = byte
            }
        }

        return byteString(result)
    }

    fun hashByArray(array: Array<String>): String

    fun hashByString(string: String): String

    fun sha256(path: Path): String

    private fun byteString(bytes: ByteArray): String {
        val sb = StringBuilder(2 * bytes.size)
        for (b in bytes) {
            sb.append(HEX_DIGITS[(b.toInt() shr 4) and 0xf]).append(HEX_DIGITS[b.toInt() and 0xf])
        }
        return sb.toString()
    }
}
