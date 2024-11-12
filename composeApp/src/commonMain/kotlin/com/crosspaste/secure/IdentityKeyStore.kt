package com.crosspaste.secure

import dev.whyoleg.cryptography.algorithms.ECDSA

interface IdentityKeyStore {

    fun saveIdentity(
        appInstanceId: String,
        identityKey: ECDSA.PublicKey,
    ): Boolean

    fun isTrustedIdentity(
        appInstanceId: String,
        identityKey: ECDSA.PublicKey,
    ): Boolean

    fun getIdentityKey(appInstanceId: String): ECDSA.PublicKey?
}