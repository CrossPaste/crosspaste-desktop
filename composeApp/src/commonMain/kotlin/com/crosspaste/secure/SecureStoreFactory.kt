package com.crosspaste.secure

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.ECDSA

interface SecureStoreFactory {

    fun createSecureStore(): SecureStore

    fun generateSecureKeyPair(): SecureKeyPair {
        val provider = CryptographyProvider.Default
        val ecdsa = provider.get(ECDSA)
        val ecdh = provider.get(ECDH)
        val signKeyPairGenerator = ecdsa.keyPairGenerator(EC.Curve.P256)
        val signKeyPair = signKeyPairGenerator.generateKeyBlocking()

        val cryptKeyPairGenerator = ecdh.keyPairGenerator(EC.Curve.P256)
        val cryptKeyPair = cryptKeyPairGenerator.generateKeyBlocking()
        return SecureKeyPair(signKeyPair, cryptKeyPair)
    }
}
