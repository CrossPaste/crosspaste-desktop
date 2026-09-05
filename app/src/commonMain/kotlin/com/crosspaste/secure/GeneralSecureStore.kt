package com.crosspaste.secure

import com.crosspaste.db.secure.SecureIO
import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.utils.StripedMutex
import io.ktor.util.collections.*

class GeneralSecureStore(
    override val secureKeyPair: SecureKeyPair,
    private val secureKeyPairSerializer: SecureKeyPairSerializer,
    private val secureIO: SecureIO,
) : SecureStore {
    private val processors = ConcurrentMap<String, SecureMessageProcessor>()
    private val peerMutex = StripedMutex()

    // @VisibleForTesting
    internal val cachedProcessorCount: Int
        get() = processors.size

    override suspend fun saveCryptPublicKey(
        appInstanceId: String,
        cryptPublicKey: ByteArray,
    ) {
        peerMutex.withLock(appInstanceId) {
            processors.remove(appInstanceId)
            secureIO.saveCryptPublicKey(appInstanceId, cryptPublicKey)
        }
    }

    override suspend fun existCryptPublicKey(appInstanceId: String): Boolean =
        peerMutex.withLock(appInstanceId) {
            secureIO.existCryptPublicKey(appInstanceId)
        }

    override suspend fun deleteCryptPublicKey(appInstanceId: String) {
        peerMutex.withLock(appInstanceId) {
            processors.remove(appInstanceId)
            secureIO.deleteCryptPublicKey(appInstanceId)
        }
    }

    override suspend fun getMessageProcessor(appInstanceId: String): SecureMessageProcessor {
        processors[appInstanceId]?.let { return it }

        // Keep unknown peers out of the striped lock. Known cache misses pay a
        // second read once, then use the processor cache for subsequent calls.
        secureIO.serializedPublicKey(appInstanceId) ?: throwMissingKey(appInstanceId)

        return peerMutex.withLock(appInstanceId) {
            processors[appInstanceId]?.let { return@withLock it }

            secureIO.serializedPublicKey(appInstanceId)?.let { publicKey ->
                val cryptPublicKey = secureKeyPairSerializer.decodeCryptPublicKey(publicKey)
                val cryptPrivateKey = secureKeyPair.cryptKeyPair.privateKey

                SecureMessageProcessor(cryptPrivateKey, cryptPublicKey).also {
                    processors[appInstanceId] = it
                }
            } ?: throwMissingKey(appInstanceId)
        }
    }

    private fun throwMissingKey(appInstanceId: String): Nothing =
        throw PasteException(
            StandardErrorCode.ENCRYPT_FAIL.toErrorCode(),
            "Crypt public key not found by appInstanceId: $appInstanceId",
        )
}
