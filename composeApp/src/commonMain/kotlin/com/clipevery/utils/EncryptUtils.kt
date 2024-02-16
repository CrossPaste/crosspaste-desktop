package com.clipevery.utils

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

fun generateAESKey(): SecretKey {
    val keyGen = KeyGenerator.getInstance("AES")
    keyGen.init(256)
    return keyGen.generateKey()
}

fun secretKeyToString(secretKey: SecretKey): String {
    val encodedKey = secretKey.encoded
    return base64Encode(encodedKey)
}

fun stringToSecretKey(encodedKey: String): SecretKey {
    val decodedKey = base64Decode(encodedKey)
    return SecretKeySpec(decodedKey, 0, decodedKey.size, "AES")
}

fun base64Encode(bytes: ByteArray): String {
    return Base64.getEncoder().encodeToString(bytes)
}

fun base64Decode(string: String): ByteArray {
    return Base64.getDecoder().decode(string)
}

fun base64mimeEncode(bytes: ByteArray): String {
    return Base64.getMimeEncoder().encodeToString(bytes)
}

fun base64mimeDecode(string: String): ByteArray {
    return Base64.getMimeDecoder().decode(string)
}

fun encryptData(key: SecretKey, data: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val ivBytes = ByteArray(cipher.blockSize)
    SecureRandom().nextBytes(ivBytes)
    val ivSpec = IvParameterSpec(ivBytes)

    cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec)
    val encrypted = cipher.doFinal(data)
    return ivBytes + encrypted
}

fun decryptData(key: SecretKey, encryptedData: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val ivBytes = encryptedData.copyOfRange(0, 16)
    val actualEncryptedData = encryptedData.copyOfRange(16, encryptedData.size)

    val ivSpec = IvParameterSpec(ivBytes)
    cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)

    return cipher.doFinal(actualEncryptedData)
}

fun md5(bytes: ByteArray): String {
    val md = java.security.MessageDigest.getInstance("MD5")
    val digest = md.digest(bytes)
    return digest.fold("") { str, it -> str + "%02x".format(it) }
}

fun md5ByArray(array: Array<String>): String {
    val outputStream = ByteArrayOutputStream()
    array.forEach {
        outputStream.write(it.toByteArray())
    }
    return md5(outputStream.toByteArray())
}

fun md5ByString(string: String): String {
    return md5(string.toByteArray())
}