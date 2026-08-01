package com.crosspaste.secure

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** HMAC authentication derived independently from the long-term ECDH secret. */
class PeerAuthenticator(
    private val privateKey: ECDH.PrivateKey,
    private val publicKey: ECDH.PublicKey,
) {
    private val hmac = CryptographyProvider.Default.get(HMAC)
    private val keyMutex = Mutex()
    private var authenticationKey: ByteArray? = null

    suspend fun authenticationCode(data: ByteArray): ByteArray =
        hmacSha256(getAuthenticationKey(), AUTHENTICATION_DOMAIN + data)

    suspend fun verify(
        data: ByteArray,
        expectedCode: ByteArray,
    ): Boolean = constantTimeEquals(authenticationCode(data), expectedCode)

    private suspend fun getAuthenticationKey(): ByteArray {
        authenticationKey?.let { return it }
        return keyMutex.withLock {
            authenticationKey?.let { return it }
            val sharedSecret =
                privateKey
                    .sharedSecretGenerator()
                    .generateSharedSecretToByteArray(publicKey)
            try {
                val extracted = hmacSha256(HKDF_SALT, sharedSecret)
                try {
                    hmacSha256(extracted, HKDF_INFO + byteArrayOf(1))
                        .also { authenticationKey = it }
                } finally {
                    extracted.fill(0)
                }
            } finally {
                sharedSecret.fill(0)
            }
        }
    }

    private suspend fun hmacSha256(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray =
        hmac
            .keyDecoder(SHA256)
            .decodeFromByteArray(HMAC.Key.Format.RAW, key)
            .signatureGenerator()
            .generateSignature(data)

    private fun constantTimeEquals(
        first: ByteArray,
        second: ByteArray,
    ): Boolean {
        if (first.size != second.size) return false
        var difference = 0
        first.indices.forEach { index ->
            difference = difference or (first[index].toInt() xor second[index].toInt())
        }
        return difference == 0
    }

    companion object {
        private val HKDF_SALT = "crosspaste-peer-auth-hkdf-v1".encodeToByteArray()
        private val HKDF_INFO = "crosspaste-peer-auth-key-v1".encodeToByteArray()
        private val AUTHENTICATION_DOMAIN = "crosspaste-peer-auth-message-v1".encodeToByteArray()
    }
}
