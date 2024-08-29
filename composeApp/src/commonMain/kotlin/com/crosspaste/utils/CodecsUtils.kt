package com.crosspaste.utils

import okio.Path

expect fun getCodecsUtils(): CodecsUtils

interface CodecsUtils {

    fun base64Encode(bytes: ByteArray): String

    fun base64Decode(string: String): ByteArray

    fun base64mimeEncode(bytes: ByteArray): String

    fun base64mimeDecode(string: String): ByteArray

    fun hash(bytes: ByteArray): String

    fun hashByArray(array: Array<String>): String

    fun hashByString(string: String): String

    fun sha256(path: Path): String
}
