package com.crosspaste.realm.secure

import io.realm.kotlin.Realm

class SecureRealm(private val realm: Realm) {
    fun saveIdentity(
        appInstanceId: String,
        serialized: ByteArray,
    ): Boolean {
        return realm.writeBlocking {
            val identityKey = query(PasteIdentityKey::class, "appInstanceId == $0", appInstanceId)
                .first()
                .find()

            identityKey?.let {
                identityKey.serialized = serialized
                return@writeBlocking true
            }

            val newPasteIdentityKey = PasteIdentityKey().apply {
                this.appInstanceId = appInstanceId
                this.serialized = serialized
            }
            copyToRealm(newPasteIdentityKey)
            return@writeBlocking false
        }
    }

    fun serializedIdentity(appInstanceId: String): ByteArray? {
        return realm.query(PasteIdentityKey::class, "appInstanceId == $0", appInstanceId)
            .first()
            .find()?.serialized
    }
}