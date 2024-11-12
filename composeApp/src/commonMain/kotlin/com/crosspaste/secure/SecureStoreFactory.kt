package com.crosspaste.secure

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA

interface SecureStoreFactory {

    fun createSecureStore(): SecureStore

    fun generateIdentityKeyPair(): ECDSA.KeyPair {
        val provider = CryptographyProvider.Default
        val ecdsa = provider.get(ECDSA)
        val keyPairGenerator = ecdsa.keyPairGenerator(EC.Curve.P256)
        val keyPair = keyPairGenerator.generateKeyBlocking()
        return keyPair
    }
}