package com.crosspaste.secure

import com.crosspaste.utils.CryptographyUtils.generateSecureKeyPair
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * Desktop's [SecureMessageProcessor] and core's [SecureMessageCipher] (used by
 * the Chrome extension via the K/JS export) must stay byte-compatible: WS
 * envelopes encrypted by one must decrypt with the other.
 */
class SecureMessageCipherInteropTest {

    @Test
    fun processorOutputDecryptsWithCipherAndBack() =
        runBlocking {
            val desktopKeys = generateSecureKeyPair()
            val extensionKeys = generateSecureKeyPair()
            val processor =
                SecureMessageProcessor(
                    desktopKeys.cryptKeyPair.privateKey,
                    extensionKeys.cryptKeyPair.publicKey,
                )
            val cipher =
                SecureMessageCipher(
                    extensionKeys.cryptKeyPair.privateKey,
                    desktopKeys.cryptKeyPair.publicKey,
                )
            val message = "encrypted ws paste push".encodeToByteArray()

            assertContentEquals(message, cipher.decrypt(processor.encrypt(message)))
            assertContentEquals(message, processor.decrypt(cipher.encrypt(message)))
        }
}
