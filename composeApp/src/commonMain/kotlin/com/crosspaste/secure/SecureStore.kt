package com.crosspaste.secure

import com.crosspaste.realm.secure.SecureRealm
import dev.whyoleg.cryptography.algorithms.ECDSA

class SecureStore(
    private val identityKeyPair: ECDSA.KeyPair,
    private val ecdsaSerializer: ECDSASerializer,
    private val secureRealm: SecureRealm,
) {

    fun saveIdentity(appInstanceId: String, identityKey: ByteArray) {

    }

    fun existIdentity(appInstanceId: String): Boolean {

    }

    fun deleteIdentity(appInstanceId: String) {

    }

    fun getPublicKey(): ByteArray {
        return ecdsaSerializer.encodePublicKey(identityKeyPair.publicKey)
    }

    fun getPrivateKey(): ByteArray {
        return ecdsaSerializer.encodePrivateKey(identityKeyPair.privateKey)
    }
    
    fun getMessageProcessor(appInstanceId: String): SecureMessageProcessor {

    }

    fun removeMessageProcessor(appInstanceId: String) {

    }
}