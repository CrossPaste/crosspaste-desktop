package com.crosspaste.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EncryptUtilsTest {

    @Test
    fun `encrypt and decrypt round-trip preserves text data`() {
        val key = EncryptUtils.generateAESKey()
        val original = "Hello, CrossPaste!".toByteArray()
        val encrypted = EncryptUtils.encryptData(key, original)
        val decrypted = EncryptUtils.decryptData(key, encrypted)
        assertEquals(String(original), String(decrypted))
    }

    @Test
    fun `encrypt and decrypt round-trip preserves binary data`() {
        val key = EncryptUtils.generateAESKey()
        val original = ByteArray(1024) { (it % 256).toByte() }
        val encrypted = EncryptUtils.encryptData(key, original)
        val decrypted = EncryptUtils.decryptData(key, encrypted)
        assertTrue(original.contentEquals(decrypted))
    }

    @Test
    fun `encrypted data is longer than original due to IV prefix`() {
        val key = EncryptUtils.generateAESKey()
        val original = "short".toByteArray()
        val encrypted = EncryptUtils.encryptData(key, original)
        // AES CBC block size = 16 bytes for IV, plus at least 1 block for padded data
        assertTrue(encrypted.size > original.size + 16)
    }

    @Test
    fun `encrypting same data twice produces different ciphertext due to random IV`() {
        val key = EncryptUtils.generateAESKey()
        val data = "same data".toByteArray()
        val encrypted1 = EncryptUtils.encryptData(key, data)
        val encrypted2 = EncryptUtils.encryptData(key, data)
        assertFalse(
            encrypted1.contentEquals(encrypted2),
            "Same plaintext should produce different ciphertext due to random IV",
        )
    }

    @Test
    fun `decrypt with wrong key throws exception`() {
        val key1 = EncryptUtils.generateAESKey()
        val key2 = EncryptUtils.generateAESKey()
        val data = "secret message".toByteArray()
        val encrypted = EncryptUtils.encryptData(key1, data)
        val result = runCatching { EncryptUtils.decryptData(key2, encrypted) }
        assertTrue(result.isFailure, "Decrypting with wrong key should fail")
    }

    @Test
    fun `secretKeyToString and stringToSecretKey round-trip preserves key`() {
        val originalKey = EncryptUtils.generateAESKey()
        val keyString = EncryptUtils.secretKeyToString(originalKey)
        val restoredKey = EncryptUtils.stringToSecretKey(keyString)
        assertTrue(
            originalKey.encoded.contentEquals(restoredKey.encoded),
            "Key bytes should be preserved after round-trip",
        )
    }

    @Test
    fun `restored key can decrypt data encrypted with original key`() {
        val originalKey = EncryptUtils.generateAESKey()
        val data = "cross-device encryption test".toByteArray()
        val encrypted = EncryptUtils.encryptData(originalKey, data)

        // Simulate key transfer: serialize and restore
        val keyString = EncryptUtils.secretKeyToString(originalKey)
        val restoredKey = EncryptUtils.stringToSecretKey(keyString)
        val decrypted = EncryptUtils.decryptData(restoredKey, encrypted)
        assertEquals(String(data), String(decrypted))
    }

    @Test
    fun `generated AES key is 256-bit`() {
        val key = EncryptUtils.generateAESKey()
        assertEquals(32, key.encoded.size, "AES-256 key should be 32 bytes")
        assertEquals("AES", key.algorithm)
    }

    @Test
    fun `each generated key is unique`() {
        val key1 = EncryptUtils.generateAESKey()
        val key2 = EncryptUtils.generateAESKey()
        assertFalse(key1.encoded.contentEquals(key2.encoded))
    }

    @Test
    fun `encrypt and decrypt empty data`() {
        val key = EncryptUtils.generateAESKey()
        val original = ByteArray(0)
        val encrypted = EncryptUtils.encryptData(key, original)
        val decrypted = EncryptUtils.decryptData(key, encrypted)
        assertTrue(original.contentEquals(decrypted))
    }

    @Test
    fun `tampered ciphertext causes decryption failure`() {
        val key = EncryptUtils.generateAESKey()
        val data = "important data".toByteArray()
        val encrypted = EncryptUtils.encryptData(key, data)

        // Tamper with a byte in the encrypted portion (after IV)
        val tampered = encrypted.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0xFF).toByte()

        val result = runCatching { EncryptUtils.decryptData(key, tampered) }
        assertTrue(result.isFailure, "Tampered ciphertext should fail decryption")
    }

    @Test
    fun `stringToSecretKey produces correct algorithm`() {
        val key = EncryptUtils.generateAESKey()
        val keyStr = EncryptUtils.secretKeyToString(key)
        val restored = EncryptUtils.stringToSecretKey(keyStr)
        assertEquals("AES", restored.algorithm)
    }

    @Test
    fun `secretKeyToString produces non-empty base64 string`() {
        val key = EncryptUtils.generateAESKey()
        val keyStr = EncryptUtils.secretKeyToString(key)
        assertTrue(keyStr.isNotEmpty())
        // Base64 strings should only contain valid base64 characters
        assertNotEquals(keyStr, String(key.encoded), "Should be base64 encoded, not raw bytes")
    }
}
