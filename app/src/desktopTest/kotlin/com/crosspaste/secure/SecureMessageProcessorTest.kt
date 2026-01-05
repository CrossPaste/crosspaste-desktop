package com.crosspaste.secure

import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.utils.CryptographyUtils.generateSecureKeyPair
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.IllegalBlockSizeException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SecureMessageProcessorTest {

    @Test
    fun testEncryptDecrypt() {
        // Generate a test key pair
        val aSecureKeyPair = generateSecureKeyPair()
        val bSecureKeyPair = generateSecureKeyPair()

        // Generate a test message
        val message = "Hello, world!".encodeToByteArray()

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
            runCatching {
                val messageBytes = message.encodeToByteArray()
                val encryptedMessage = aProcessor.encrypt(messageBytes)
                val decryptedMessage = bProcessor.decrypt(encryptedMessage)

                assertContentEquals(
                    messageBytes,
                    decryptedMessage,
                    "Failed for message length ${messageBytes.size}: $message",
                )
            }.onFailure { e ->
                when (e) {
                    is IllegalBlockSizeException -> {
                        println("IllegalBlockSizeException caught for message length ${message.length}: $message")
                    }
                    else -> {
                        println(
                            "Unexpected exception for message length " +
                                "${message.length}: ${e.javaClass.simpleName} - ${e.message}",
                        )
                    }
                }
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
        val largeMessage = "X".repeat(1024 * 1024).encodeToByteArray()

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

        val message = "Test message with special chars: !@#$%^&*()".encodeToByteArray()
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

        val message = "Original message".encodeToByteArray()
        val encrypted = aProcessor.encrypt(message)

        for (i in encrypted.indices step (encrypted.size / 4)) {
            val modifiedEncrypted = encrypted.clone()
            modifiedEncrypted[i] = (modifiedEncrypted[i] + 1).toByte()

            runCatching {
                bProcessor.decrypt(modifiedEncrypted)
            }.onFailure { e ->
                println("Expected exception with modified data at position $i: ${e.javaClass.name} - ${e.message}")
            }
        }
    }

    @Test
    fun testDecryptFailException() {
        val aSecureKeyPair = generateSecureKeyPair()
        val bSecureKeyPair = generateSecureKeyPair()

        val processor =
            SecureMessageProcessor(
                bSecureKeyPair.cryptKeyPair.privateKey,
                aSecureKeyPair.cryptKeyPair.publicKey,
            )

        val invalidData = byteArrayOf(1, 2, 3, 4, 5)

        val exception =
            assertFailsWith<PasteException> {
                processor.decrypt(invalidData)
            }

        assertTrue(
            exception.match(StandardErrorCode.DECRYPT_FAIL),
            "Exception should match DECRYPT_FAIL error code",
        )
    }

    @Test
    fun testEncryptWithEmptyData() {
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

        val emptyData = byteArrayOf()
        val encrypted = aProcessor.encrypt(emptyData)
        val decrypted = bProcessor.decrypt(encrypted)

        assertContentEquals(emptyData, decrypted, "Empty data should encrypt/decrypt correctly")
    }

    @Test
    fun testDecryptWithEmptyData() {
        val aSecureKeyPair = generateSecureKeyPair()
        val bSecureKeyPair = generateSecureKeyPair()

        val processor =
            SecureMessageProcessor(
                bSecureKeyPair.cryptKeyPair.privateKey,
                aSecureKeyPair.cryptKeyPair.publicKey,
            )

        val emptyData = byteArrayOf()

        val exception =
            assertFailsWith<PasteException> {
                processor.decrypt(emptyData)
            }

        assertTrue(
            exception.match(StandardErrorCode.DECRYPT_FAIL),
            "Decrypting empty data should throw DECRYPT_FAIL exception",
        )
    }

    @Test
    fun testEncryptionConsistency() {
        val aSecureKeyPair = generateSecureKeyPair()
        val bSecureKeyPair = generateSecureKeyPair()

        val processor =
            SecureMessageProcessor(
                aSecureKeyPair.cryptKeyPair.privateKey,
                bSecureKeyPair.cryptKeyPair.publicKey,
            )

        val message = "Test message for consistency".encodeToByteArray()
        val encrypted1 = processor.encrypt(message)
        val encrypted2 = processor.encrypt(message)

        // Different encryptions of same data should be different due to IV
        assertNotEquals(
            encrypted1.contentHashCode(),
            encrypted2.contentHashCode(),
            "Encrypted data should be different each time due to random IV",
        )
    }

    @Test
    fun testDecryptionWithWrongKeyPair() {
        val aSecureKeyPair = generateSecureKeyPair()
        val bSecureKeyPair = generateSecureKeyPair()
        var cSecureKeyPair = generateSecureKeyPair()

        // Ensure that cSecureKeyPair is different from bSecureKeyPair
        // to avoid accidental derivation of the same shared secret
        // Although the probability is low with a strong SecureRandom
        // this guards against seed collision in fast-running tests
        while (cSecureKeyPair.cryptKeyPair.publicKey == bSecureKeyPair.cryptKeyPair.publicKey) {
            cSecureKeyPair = generateSecureKeyPair()
        }

        val aProcessor =
            SecureMessageProcessor(
                aSecureKeyPair.cryptKeyPair.privateKey,
                bSecureKeyPair.cryptKeyPair.publicKey,
            )

        val wrongProcessor =
            SecureMessageProcessor(
                cSecureKeyPair.cryptKeyPair.privateKey,
                aSecureKeyPair.cryptKeyPair.publicKey,
            )

        val message = "Test message".encodeToByteArray()
        val encrypted = aProcessor.encrypt(message)

        val exception =
            assertFailsWith<PasteException> {
                wrongProcessor.decrypt(encrypted)
            }

        assertTrue(
            exception.match(StandardErrorCode.DECRYPT_FAIL),
            "Decryption with wrong key pair should throw DECRYPT_FAIL exception",
        )
    }

    @Test
    fun testConcurrentEncryptionDecryption() =
        runBlocking {
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

            val numberOfThreads = 10
            val results = ConcurrentHashMap<Int, Boolean>()

            val jobs =
                (0 until numberOfThreads).map { threadId ->
                    async {
                        try {
                            val message = "Thread $threadId message".encodeToByteArray()
                            val encrypted = aProcessor.encrypt(message)
                            val decrypted = bProcessor.decrypt(encrypted)
                            results[threadId] = message.contentEquals(decrypted)
                        } catch (_: Exception) {
                            results[threadId] = false
                        }
                    }
                }

            jobs.awaitAll()

            assertEquals(
                numberOfThreads,
                results.size,
                "All threads should complete",
            )

            assertTrue(
                results.values.all { it },
                "All concurrent operations should succeed",
            )
        }

    @Test
    fun testProcessorInitializationWithSameKeyPair() {
        val secureKeyPair = generateSecureKeyPair()

        // Using same key pair for both private and public key should work
        val processor =
            SecureMessageProcessor(
                secureKeyPair.cryptKeyPair.privateKey,
                secureKeyPair.cryptKeyPair.publicKey,
            )

        val message = "Self-encryption test".encodeToByteArray()
        val encrypted = processor.encrypt(message)
        val decrypted = processor.decrypt(encrypted)

        assertContentEquals(
            message,
            decrypted,
            "Self-encryption should work with same key pair",
        )
    }

    @Test
    fun testExceptionCausePreservation() {
        val aSecureKeyPair = generateSecureKeyPair()
        val bSecureKeyPair = generateSecureKeyPair()

        val processor =
            SecureMessageProcessor(
                bSecureKeyPair.cryptKeyPair.privateKey,
                aSecureKeyPair.cryptKeyPair.publicKey,
            )

        val invalidData = byteArrayOf(1, 2, 3)

        val exception =
            assertFailsWith<PasteException> {
                processor.decrypt(invalidData)
            }

        // Verify that the original exception is preserved as cause
        assertTrue(
            exception.cause != null,
            "Original exception should be preserved as cause",
        )
    }

    @Test
    fun testLargeDataEncryption() {
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

        // Test with 10MB data
        val largeData = ByteArray(10 * 1024 * 1024) { (it % 256).toByte() }
        val encrypted = aProcessor.encrypt(largeData)
        val decrypted = bProcessor.decrypt(encrypted)

        assertContentEquals(
            largeData,
            decrypted,
            "Large data encryption/decryption should work correctly",
        )
    }

    @Test
    fun testBinaryDataEncryption() {
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

        // Create binary data with all possible byte values
        val binaryData = ByteArray(256) { it.toByte() }
        val encrypted = aProcessor.encrypt(binaryData)
        val decrypted = bProcessor.decrypt(encrypted)

        assertContentEquals(
            binaryData,
            decrypted,
            "Binary data with all byte values should encrypt/decrypt correctly",
        )
    }

    @Test
    fun testThreadSafety() {
        val aSecureKeyPair = generateSecureKeyPair()
        val bSecureKeyPair = generateSecureKeyPair()

        val processor =
            SecureMessageProcessor(
                aSecureKeyPair.cryptKeyPair.privateKey,
                bSecureKeyPair.cryptKeyPair.publicKey,
            )

        val numberOfThreads = 50
        val latch = CountDownLatch(numberOfThreads)
        val results = ConcurrentHashMap<Int, ByteArray>()
        val exceptions = ConcurrentHashMap<Int, Exception>()

        repeat(numberOfThreads) { threadId ->
            Thread {
                try {
                    val message = "Thread $threadId".encodeToByteArray()
                    val encrypted = processor.encrypt(message)
                    results[threadId] = encrypted
                } catch (e: Exception) {
                    exceptions[threadId] = e
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        assertTrue(
            latch.await(30, TimeUnit.SECONDS),
            "All threads should complete within timeout",
        )

        assertEquals(
            0,
            exceptions.size,
            "No exceptions should occur during concurrent access: ${exceptions.values.map { it.message }}",
        )

        assertEquals(
            numberOfThreads,
            results.size,
            "All threads should produce results",
        )
    }
}
