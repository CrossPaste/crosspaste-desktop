package com.crosspaste.secure

import com.crosspaste.realm.secure.SecureRealm
import dev.whyoleg.cryptography.algorithms.ECDSA

class DesktopIdentityKeyStore(
    val identityKeyPair: ECDSA.KeyPair,
    private val ecdsaSerializer: ECDSASerializer,
    private val secureRealm: SecureRealm,
): IdentityKeyStore {

    override fun saveIdentity(
        appInstanceId: String,
        identityKey: ECDSA.PublicKey,
    ): Boolean {
        val bytes = ecdsaSerializer.encodePublicKey(identityKey)
        return secureRealm.saveIdentity(appInstanceId, bytes)
    }

    override fun isTrustedIdentity(
        appInstanceId: String,
        identityKey: ECDSA.PublicKey
    ): Boolean {
        return getIdentityKey(appInstanceId)?.let {
            it == identityKey
        } ?: false
    }

    override fun getIdentityKey(appInstanceId: String): ECDSA.PublicKey? {
        return secureRealm.serializedIdentity(appInstanceId)?.let {
            ecdsaSerializer.decodePublicKey(it)
        }
    }
}