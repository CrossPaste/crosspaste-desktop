package com.crosspaste.secure

import com.crosspaste.db.secure.SecureIO
import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
import io.ktor.util.collections.*
import kotlinx.coroutines.sync.withLock

class GeneralSecureStore(
    override val secureKeyPair: SecureKeyPair,
    private val secureKeyPairSerializer: SecureKeyPairSerializer,
    private val secureIO: SecureIO,
) : SecureStore {
    private val sessions = ConcurrentMap<String, SecureSession>()

    private fun getSecureSession(appInstanceId: String): SecureSession =
        sessions.computeIfAbsent(appInstanceId) {
            SecureSession()
        }

    override suspend fun saveCryptPublicKey(
        appInstanceId: String,
        cryptPublicKey: ByteArray,
    ) {
        val session = getSecureSession(appInstanceId)
        session.mutex.withLock {
            session.processor = null
            secureIO.saveCryptPublicKey(appInstanceId, cryptPublicKey)
        }
    }

    override suspend fun existCryptPublicKey(appInstanceId: String): Boolean {
        val session = getSecureSession(appInstanceId)
        return session.mutex.withLock {
            secureIO.existCryptPublicKey(appInstanceId)
        }
    }

    override suspend fun deleteCryptPublicKey(appInstanceId: String) {
        val session = getSecureSession(appInstanceId)
        session.mutex.withLock {
            session.processor = null
            secureIO.deleteCryptPublicKey(appInstanceId)
        }
    }

    override suspend fun getMessageProcessor(appInstanceId: String): SecureMessageProcessor {
        val session = getSecureSession(appInstanceId)
        session.processor?.let { return it }

        return session.mutex.withLock {
            session.processor?.let { return it }

            secureIO.serializedPublicKey(appInstanceId)?.let { publicKey ->
                val cryptPublicKey = secureKeyPairSerializer.decodeCryptPublicKey(publicKey)
                val cryptPrivateKey = secureKeyPair.cryptKeyPair.privateKey

                SecureMessageProcessor(cryptPrivateKey, cryptPublicKey).also {
                    session.processor = it
                }
            } ?: run {
                throw PasteException(
                    StandardErrorCode.ENCRYPT_FAIL.toErrorCode(),
                    "Crypt public key not found by appInstanceId: $appInstanceId",
                )
            }
        }
    }
}
