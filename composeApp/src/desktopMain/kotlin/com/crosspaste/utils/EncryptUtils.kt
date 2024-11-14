package com.crosspaste.utils

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptUtils {

    private val codecsUtils = getCodecsUtils()

    fun generateAESKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        return keyGen.generateKey()
    }

    fun secretKeyToString(secretKey: SecretKey): String {
        val encodedKey = secretKey.encoded
        return codecsUtils.base64Encode(encodedKey)
    }

    fun stringToSecretKey(encodedKey: String): SecretKey {
        val decodedKey = codecsUtils.base64Decode(encodedKey)
        return SecretKeySpec(decodedKey, 0, decodedKey.size, "AES")
    }

    fun encryptData(
        key: SecretKey,
        data: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivBytes = ByteArray(cipher.blockSize)
        SecureRandom().nextBytes(ivBytes)
        val ivSpec = IvParameterSpec(ivBytes)

        cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec)
        val encrypted = cipher.doFinal(data)
        return ivBytes + encrypted
    }

    fun decryptData(
        key: SecretKey,
        encryptedData: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivBytes = encryptedData.copyOfRange(0, 16)
        val actualEncryptedData = encryptedData.copyOfRange(16, encryptedData.size)

        val ivSpec = IvParameterSpec(ivBytes)
        cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)

        return cipher.doFinal(actualEncryptedData)
    }
}
