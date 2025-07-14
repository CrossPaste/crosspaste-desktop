package com.crosspaste.secure

import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.ECDH
import io.github.oshai.kotlinlogging.KotlinLogging

class SecureMessageProcessor(
    privateKey: ECDH.PrivateKey,
    publicKey: ECDH.PublicKey,
) {

    private val logger = KotlinLogging.logger {}

    private val provider = CryptographyProvider.Default

    private val cipher: AES.IvCipher

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
            logger.error(e) { "Decrypt fail" }
            throw PasteException(StandardErrorCode.DECRYPT_FAIL.toErrorCode(), e)
        }
    }
}
