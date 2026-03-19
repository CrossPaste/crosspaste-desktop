package com.crosspaste.secure

import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.ECDSA

data class SecureKeyPair(
    val signKeyPair: ECDSA.KeyPair,
    val cryptKeyPair: ECDH.KeyPair,
) {

    suspend fun getSignPublicKeyBytes(secureKeyPairSerializer: SecureKeyPairSerializer): ByteArray =
        secureKeyPairSerializer.encodeSignPublicKey(signKeyPair.publicKey)

    suspend fun getCryptPublicKeyBytes(secureKeyPairSerializer: SecureKeyPairSerializer): ByteArray =
        secureKeyPairSerializer.encodeCryptPublicKey(cryptKeyPair.publicKey)
}
