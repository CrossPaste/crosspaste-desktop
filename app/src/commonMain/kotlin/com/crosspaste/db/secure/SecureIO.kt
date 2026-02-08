package com.crosspaste.db.secure

interface SecureIO {
    suspend fun saveCryptPublicKey(
        appInstanceId: String,
        serialized: ByteArray,
    )

    suspend fun existCryptPublicKey(appInstanceId: String): Boolean

    suspend fun deleteCryptPublicKey(appInstanceId: String)

    suspend fun serializedPublicKey(appInstanceId: String): ByteArray?
}