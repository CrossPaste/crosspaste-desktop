package com.crosspaste.utils

import javax.crypto.SecretKey

expect fun getEncryptUtils(): EncryptUtils

interface EncryptUtils {

    fun generateAESKey(): SecretKey

    fun secretKeyToString(secretKey: SecretKey): String

    fun stringToSecretKey(encodedKey: String): SecretKey

    fun base64Encode(bytes: ByteArray): String

    fun base64Decode(string: String): ByteArray

    fun base64mimeEncode(bytes: ByteArray): String

    fun base64mimeDecode(string: String): ByteArray

    fun encryptData(
        key: SecretKey,
        data: ByteArray,
    ): ByteArray

    fun decryptData(
        key: SecretKey,
        encryptedData: ByteArray,
    ): ByteArray

    fun md5(bytes: ByteArray): String

    fun md5ByArray(array: Array<String>): String

    fun md5ByString(string: String): String
}
