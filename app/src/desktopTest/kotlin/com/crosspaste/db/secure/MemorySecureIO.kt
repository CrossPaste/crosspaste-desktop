package com.crosspaste.db.secure

import io.ktor.util.collections.ConcurrentMap

class MemorySecureIO : SecureIO {

    private val cryptPublicKeyMap = ConcurrentMap<String, ByteArray>()

    override suspend fun saveCryptPublicKey(
        appInstanceId: String,
        serialized: ByteArray,
    ) {
        cryptPublicKeyMap[appInstanceId] = serialized
    }

    override suspend fun existCryptPublicKey(appInstanceId: String): Boolean {
        return cryptPublicKeyMap.containsKey(appInstanceId)
    }

    override suspend fun deleteCryptPublicKey(appInstanceId: String) {
        cryptPublicKeyMap.remove(appInstanceId)
    }

    override suspend fun serializedPublicKey(appInstanceId: String): ByteArray? {
        return cryptPublicKeyMap[appInstanceId]
    }
}