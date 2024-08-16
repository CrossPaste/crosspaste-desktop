package com.crosspaste.utils

import okio.Path

expect fun getCodecsUtils(): CodecsUtils

interface CodecsUtils {

    fun base64Encode(bytes: ByteArray): String

    fun base64Decode(string: String): ByteArray

    fun base64mimeEncode(bytes: ByteArray): String

    fun base64mimeDecode(string: String): ByteArray

    fun md5(bytes: ByteArray): String

    fun md5ByArray(array: Array<String>): String

    fun md5ByString(string: String): String

    fun sha256(path: Path): String
}
