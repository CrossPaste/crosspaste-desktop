package com.crosspaste.e2e.peer

import com.crosspaste.db.secure.SecureIO
import io.ktor.util.collections.ConcurrentMap

class InMemorySecureIO : SecureIO {

    private val cryptPublicKeyMap = ConcurrentMap<String, ByteArray>()

    override suspend fun saveCryptPublicKey(
        appInstanceId: String,
        serialized: ByteArray,
    ) {
        cryptPublicKeyMap[appInstanceId] = serialized
    }

    override suspend fun existCryptPublicKey(appInstanceId: String): Boolean =
        cryptPublicKeyMap.containsKey(appInstanceId)

    override suspend fun deleteCryptPublicKey(appInstanceId: String) {
        cryptPublicKeyMap.remove(appInstanceId)
    }

    override suspend fun serializedPublicKey(appInstanceId: String): ByteArray? = cryptPublicKeyMap[appInstanceId]
}
