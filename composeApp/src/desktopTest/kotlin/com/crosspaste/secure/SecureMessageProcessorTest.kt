package com.crosspaste.secure

import com.crosspaste.utils.CryptographyUtils.generateSecureKeyPair
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
}
