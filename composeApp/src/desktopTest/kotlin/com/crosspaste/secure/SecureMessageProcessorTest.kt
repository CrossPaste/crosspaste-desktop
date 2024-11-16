package com.crosspaste.secure

import com.crosspaste.utils.CryptographyUtils.generateSecureKeyPair
import java.util.Base64
import javax.crypto.IllegalBlockSizeException
import kotlin.test.Test
import kotlin.test.assertContentEquals

class SecureMessageProcessorTest {

    @Test
    fun testEncryptDecrypt() {
        // Generate a test key pair
        val aSecureKeyPair = generateSecureKeyPair()
        val bSecureKeyPair = generateSecureKeyPair()

        // Generate a test message
        val message = "Hello, world!".toByteArray()

        val aProcessor =
            SecureMessageProcessor(
                aSecureKeyPair.cryptKeyPair.privateKey,
                bSecureKeyPair.cryptKeyPair.publicKey,
            )

        val bProcessor =
            SecureMessageProcessor(
                bSecureKeyPair.cryptKeyPair.privateKey,
                aSecureKeyPair.cryptKeyPair.publicKey,
            )

        // Encrypt the message
        val encryptedMessage = aProcessor.encrypt(message)

        // Decrypt the message
        val decryptedMessage = bProcessor.decrypt(encryptedMessage)

        // Compare the original message with the decrypted message
        assertContentEquals(
            message,
            decryptedMessage,
            "Decrypted message should match original message",
        )
    }

    @Test
    fun testEncryptDecryptWithVariousLengths() {
        val aSecureKeyPair = generateSecureKeyPair()
        val bSecureKeyPair = generateSecureKeyPair()

        val aProcessor =
            SecureMessageProcessor(
                aSecureKeyPair.cryptKeyPair.privateKey,
                bSecureKeyPair.cryptKeyPair.publicKey,
            )

        val bProcessor =
            SecureMessageProcessor(
                bSecureKeyPair.cryptKeyPair.privateKey,
                aSecureKeyPair.cryptKeyPair.publicKey,
            )

        // Test with messages of different lengths
        val testMessages =
            listOf(
                // 15 bytes (not multiple of 16)
                "A".repeat(15),
                // 16 bytes (exact multiple)
                "B".repeat(16),
                // 17 bytes (one more than multiple)
                "C".repeat(17),
                // 31 bytes (not multiple of 16)
                "D".repeat(31),
                // 32 bytes (exact multiple)
                "E".repeat(32),
                // Empty message
                "",
                // 6 bytes (not multiple of 16)
                "Hello!",
            )

        for (message in testMessages) {
            try {
                val messageBytes = message.toByteArray()
                val encryptedMessage = aProcessor.encrypt(messageBytes)
                val decryptedMessage = bProcessor.decrypt(encryptedMessage)

                assertContentEquals(
                    messageBytes,
                    decryptedMessage,
                    "Failed for message length ${messageBytes.size}: $message",
                )
            } catch (e: IllegalBlockSizeException) {
                println("IllegalBlockSizeException caught for message length ${message.length}: $message")
                throw e
            } catch (e: Exception) {
                println("Unexpected exception for message length ${message.length}: ${e.javaClass.simpleName} - ${e.message}")
                throw e
            }
        }
    }

    @Test
    fun testLargeMessageEncryption() {
        val aSecureKeyPair = generateSecureKeyPair()
        val bSecureKeyPair = generateSecureKeyPair()

        val aProcessor =
            SecureMessageProcessor(
                aSecureKeyPair.cryptKeyPair.privateKey,
                bSecureKeyPair.cryptKeyPair.publicKey,
            )

        val bProcessor =
            SecureMessageProcessor(
                bSecureKeyPair.cryptKeyPair.privateKey,
                aSecureKeyPair.cryptKeyPair.publicKey,
            )

        // Create a large message (1MB)
        val largeMessage = "X".repeat(1024 * 1024).toByteArray()

        // This might throw IllegalBlockSizeException
        val encryptedMessage = aProcessor.encrypt(largeMessage)
        val decryptedMessage = bProcessor.decrypt(encryptedMessage)

        assertContentEquals(
            largeMessage,
            decryptedMessage,
            "Large message encryption/decryption failed",
        )
    }

    @Test
    fun testWithBase64Encoding() {
        val aSecureKeyPair = generateSecureKeyPair()
        val bSecureKeyPair = generateSecureKeyPair()

        val aProcessor =
            SecureMessageProcessor(
                aSecureKeyPair.cryptKeyPair.privateKey,
                bSecureKeyPair.cryptKeyPair.publicKey,
            )

        val bProcessor =
            SecureMessageProcessor(
                bSecureKeyPair.cryptKeyPair.privateKey,
                aSecureKeyPair.cryptKeyPair.publicKey,
            )

        val message = "Test message with special chars: !@#$%^&*()".toByteArray()
        val encrypted = aProcessor.encrypt(message)

        val base64Encrypted = Base64.getEncoder().encodeToString(encrypted)
        val receivedEncrypted = Base64.getDecoder().decode(base64Encrypted)

        val decrypted = bProcessor.decrypt(receivedEncrypted)
        assertContentEquals(message, decrypted)
    }

    @Test
    fun testWithModifiedData() {
        val aSecureKeyPair = generateSecureKeyPair()
        val bSecureKeyPair = generateSecureKeyPair()

        val aProcessor =
            SecureMessageProcessor(
                aSecureKeyPair.cryptKeyPair.privateKey,
                bSecureKeyPair.cryptKeyPair.publicKey,
            )

        val bProcessor =
            SecureMessageProcessor(
                bSecureKeyPair.cryptKeyPair.privateKey,
                aSecureKeyPair.cryptKeyPair.publicKey,
            )

        val message = "Original message".toByteArray()
        val encrypted = aProcessor.encrypt(message)

        for (i in encrypted.indices step (encrypted.size / 4)) {
            val modifiedEncrypted = encrypted.clone()
            modifiedEncrypted[i] = (modifiedEncrypted[i] + 1).toByte()

            try {
                bProcessor.decrypt(modifiedEncrypted)
            } catch (e: Exception) {
                println("Expected exception with modified data at position $i: ${e.javaClass.name} - ${e.message}")
            }
        }
    }
}
