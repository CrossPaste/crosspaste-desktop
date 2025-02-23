package com.crosspaste.db.secure

interface SecureIO {
    fun saveCryptPublicKey(
        appInstanceId: String,
        serialized: ByteArray,
    ): Boolean

    fun existCryptPublicKey(appInstanceId: String): Boolean

    fun deleteCryptPublicKey(appInstanceId: String)

    fun serializedPublicKey(appInstanceId: String): ByteArray?
}