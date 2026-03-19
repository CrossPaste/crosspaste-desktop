package com.crosspaste.utils

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

val HEX_DIGITS: CharArray = "0123456789abcdef".toCharArray()

val CROSS_PASTE_SEED = 13043025u

val CROSSPASTE_HASH = MurmurHash3(CROSS_PASTE_SEED)

expect fun getCodecsUtils(): CodecsUtils

interface CodecsUtils {

    @OptIn(ExperimentalEncodingApi::class)
    fun base64Encode(bytes: ByteArray): String = Base64.encode(bytes)

    @OptIn(ExperimentalEncodingApi::class)
    fun base64Decode(string: String): ByteArray = Base64.decode(string)

    fun hash(bytes: ByteArray): String =
        if (bytes.isEmpty()) {
            ""
        } else {
            val (hash1, hash2) = CROSSPASTE_HASH.hash128x64(bytes)
            buildString(32) {
                appendHex(hash1)
                appendHex(hash2)
            }
        }

    fun hashByString(string: String): String = hash(string.encodeToByteArray())

    fun hashByArray(array: Array<String>): String
}
