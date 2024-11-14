package com.crosspaste.realm.secure

import io.realm.kotlin.Realm

class SecureRealm(private val realm: Realm) {
    fun saveCryptPublicKey(
        appInstanceId: String,
        serialized: ByteArray,
    ): Boolean {
        return realm.writeBlocking {
            val cryptPublicKey =
                query(CryptPublicKey::class, "appInstanceId == $0", appInstanceId)
                    .first()
                    .find()

            cryptPublicKey?.let {
                cryptPublicKey.serialized = serialized
                return@writeBlocking true
            }

            val newCryptPublicKey = CryptPublicKey(appInstanceId, serialized)

            copyToRealm(newCryptPublicKey)
            return@writeBlocking false
        }
    }

    fun existCryptPublicKey(appInstanceId: String): Boolean {
        return realm.query(CryptPublicKey::class, "appInstanceId == $0", appInstanceId)
            .first()
            .find() != null
    }

    fun deleteCryptPublicKey(appInstanceId: String) {
        return realm.writeBlocking {
            query(CryptPublicKey::class, "appInstanceId == $0", appInstanceId)
                .first()
                .find()?.let {
                    delete(it)
                }
        }
    }

    fun serializedIdentity(appInstanceId: String): ByteArray? {
        return realm.query(CryptPublicKey::class, "appInstanceId == $0", appInstanceId)
            .first()
            .find()?.serialized
    }
}
