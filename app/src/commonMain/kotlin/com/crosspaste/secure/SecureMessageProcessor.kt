package com.crosspaste.secure

import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.operations.IvCipher
import io.github.oshai.kotlinlogging.KotlinLogging

class SecureMessageProcessor(
    privateKey: ECDH.PrivateKey,
    publicKey: ECDH.PublicKey,
) {

    private val logger = KotlinLogging.logger {}

    private val provider = CryptographyProvider.Default

    private val cipher: IvCipher
    private val peerAuthenticator: PeerAuthenticator

    init {
        val aes = provider.get(AES.CBC)
        val bytes =
            privateKey
                .sharedSecretGenerator()
                .generateSharedSecretToByteArrayBlocking(publicKey)
        val key =
            aes
                .keyDecoder()
                .decodeFromByteArrayBlocking(AES.Key.Format.RAW, bytes)
        cipher = key.cipher()
        peerAuthenticator = PeerAuthenticator(privateKey, publicKey)
    }

    fun encrypt(data: ByteArray): ByteArray {
        try {
            return cipher.encryptBlocking(data)
        } catch (e: Throwable) {
            logger.error(e) { "Encrypt fail" }
            throw PasteException(StandardErrorCode.ENCRYPT_FAIL.toErrorCode(), e)
        }
    }

    fun decrypt(data: ByteArray): ByteArray {
        try {
            return cipher.decryptBlocking(data)
        } catch (e: Throwable) {
            // Expected when pairing keys have diverged (peer re-paired / restored old
            // state); the caller maps it to DECRYPT_FAIL and the peer re-pairs, so a
            // warn without stack trace is enough.
            logger.warn { "Decrypt fail: ${e.message}" }
            throw PasteException(StandardErrorCode.DECRYPT_FAIL.toErrorCode(), e)
        }
    }

    suspend fun authenticationCode(data: ByteArray): ByteArray = peerAuthenticator.authenticationCode(data)

    suspend fun verifyAuthentication(
        data: ByteArray,
        expectedCode: ByteArray,
    ): Boolean = peerAuthenticator.verify(data, expectedCode)
}
