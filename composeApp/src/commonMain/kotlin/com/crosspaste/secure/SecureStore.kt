package com.crosspaste.secure

import com.crosspaste.realm.secure.SecureRealm
import io.ktor.util.collections.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SecureStore(
    private val secureKeyPair: SecureKeyPair,
    private val secureKeyPairSerializer: SecureKeyPairSerializer,
    private val secureRealm: SecureRealm,
) {
    private val instanceStates = ConcurrentMap<String, InstanceState>()

    private fun getInstanceState(appInstanceId: String): InstanceState {
        return instanceStates.computeIfAbsent(appInstanceId) { InstanceState() }
    }

    private fun removeInstanceState(appInstanceId: String): InstanceState? {
        return instanceStates.remove(appInstanceId)
    }

    suspend fun saveCryptPublicKey(
        appInstanceId: String,
        cryptPublicKey: ByteArray,
    ) {
        val state = getInstanceState(appInstanceId)
        state.mutex.withLock {
            state.processor = null
            secureRealm.saveCryptPublicKey(appInstanceId, cryptPublicKey)
        }
    }

    suspend fun existCryptPublicKey(appInstanceId: String): Boolean {
        val state = getInstanceState(appInstanceId)
        return state.mutex.withLock {
            secureRealm.existCryptPublicKey(appInstanceId)
        }
    }

    suspend fun deleteCryptPublicKey(appInstanceId: String) {
        val state = removeInstanceState(appInstanceId)
        state?.mutex?.withLock {
            state.processor = null
            secureRealm.deleteCryptPublicKey(appInstanceId)
        }
    }

    fun getSignPublicKeyBytes(): ByteArray {
        return secureKeyPairSerializer.encodeSignPublicKey(secureKeyPair.signKeyPair.publicKey)
    }

    fun getCryptPublicKeyBytes(): ByteArray {
        return secureKeyPairSerializer.encodeCryptPublicKey(secureKeyPair.cryptKeyPair.publicKey)
    }

    fun getSecureKeyPair(): SecureKeyPair {
        return secureKeyPair
    }

    suspend fun getMessageProcessor(appInstanceId: String): SecureMessageProcessor {
        val state = getInstanceState(appInstanceId)
        state.processor?.let { return it }

        return state.mutex.withLock {
            state.processor?.let { return it }

            secureRealm.serializedIdentity(appInstanceId)?.let { identityKey ->
                val cryptPublicKey = secureKeyPairSerializer.decodeCryptPublicKey(identityKey)
                val cryptPrivateKey = secureKeyPair.cryptKeyPair.privateKey

                SecureMessageProcessor(cryptPrivateKey, cryptPublicKey).also {
                    state.processor = it
                }
            } ?: run {
                throw IllegalStateException("Crypt public key not found by appInstanceId: $appInstanceId")
            }
        }
    }
}

private data class InstanceState(
    val mutex: Mutex = Mutex(),
    var processor: SecureMessageProcessor? = null,
)
