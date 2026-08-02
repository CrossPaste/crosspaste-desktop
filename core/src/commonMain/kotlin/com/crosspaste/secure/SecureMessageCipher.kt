package com.crosspaste.secure

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.operations.IvCipher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * Symmetric message cipher over the long-term ECDH secret, usable from any
 * target (suspend-only, so it also runs on the WebCrypto backend where
 * blocking operations are unavailable).
 *
 * Wire format must stay byte-compatible with desktop's `SecureMessageProcessor`
 * (app module): the raw ECDH shared secret is used directly as an AES-256 key
 * with AES-CBC, IV prepended to the ciphertext by the cryptography library.
 */
class SecureMessageCipher(
    private val privateKey: ECDH.PrivateKey,
    private val publicKey: ECDH.PublicKey,
) {
    private val aes = CryptographyProvider.Default.get(AES.CBC)
    private val cipherMutex = Mutex()

    @Volatile
    private var cipher: IvCipher? = null

    suspend fun encrypt(data: ByteArray): ByteArray = getCipher().encrypt(data)

    suspend fun decrypt(data: ByteArray): ByteArray = getCipher().decrypt(data)

    private suspend fun getCipher(): IvCipher {
        cipher?.let { return it }
        return cipherMutex.withLock {
            cipher?.let { return it }
            val sharedSecret =
                privateKey
                    .sharedSecretGenerator()
                    .generateSharedSecretToByteArray(publicKey)
            try {
                aes
                    .keyDecoder()
                    .decodeFromByteArray(AES.Key.Format.RAW, sharedSecret)
                    .cipher()
                    .also { cipher = it }
            } finally {
                sharedSecret.fill(0)
            }
        }
    }
}
