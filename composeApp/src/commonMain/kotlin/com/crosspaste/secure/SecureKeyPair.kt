package com.crosspaste.secure

import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.ECDSA

data class SecureKeyPair(
    val signKeyPair: ECDSA.KeyPair,
    val cryptKeyPair: ECDH.KeyPair,
) {

    fun getSignPublicKeyBytes(secureKeyPairSerializer: SecureKeyPairSerializer): ByteArray {
        return secureKeyPairSerializer.encodeSignPublicKey(signKeyPair.publicKey)
    }

    fun getCryptPublicKeyBytes(secureKeyPairSerializer: SecureKeyPairSerializer): ByteArray {
        return secureKeyPairSerializer.encodeCryptPublicKey(cryptKeyPair.publicKey)
    }
}
