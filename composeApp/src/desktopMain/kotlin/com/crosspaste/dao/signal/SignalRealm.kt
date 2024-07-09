package com.crosspaste.dao.signal

import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy

class SignalRealm(private val realm: Realm) : SignalDao {

    override fun existPreKey(preKeyId: Int): Boolean {
        return realm.query(PastePreKey::class, "id == $0", preKeyId).first().find() != null
    }

    override fun getSignedPreKey(signedPreKeyId: Int): PasteSignedPreKey? {
        return realm.query(PasteSignedPreKey::class, "id == $0", signedPreKeyId).first().find()
    }

    override fun saveIdentities(identityKeys: List<PasteIdentityKey>) {
        realm.writeBlocking {
            identityKeys.forEach { identityKey ->
                val newPasteIdentityKey =
                    PasteIdentityKey().apply {
                        this.appInstanceId = identityKey.appInstanceId
                        this.serialized = identityKey.serialized
                    }
                copyToRealm(newPasteIdentityKey, updatePolicy = UpdatePolicy.ALL)
            }
        }
    }

    override fun saveIdentity(
        appInstanceId: String,
        serialized: ByteArray,
    ): Boolean {
        return realm.writeBlocking {
            val pasteIdentityKey = query(PasteIdentityKey::class, "appInstanceId == $0", appInstanceId).first().find()

            pasteIdentityKey?.let {
                pasteIdentityKey.serialized = serialized
                return@writeBlocking true
            }
            val newPasteIdentityKey =
                PasteIdentityKey().apply {
                    this.appInstanceId = appInstanceId
                    this.serialized = serialized
                }
            copyToRealm(newPasteIdentityKey)
            return@writeBlocking false
        }
    }

    override fun deleteIdentity(appInstanceId: String) {
        return realm.writeBlocking {
            val pasteIdentityKey = query(PasteIdentityKey::class, "appInstanceId == $0", appInstanceId).first().find()
            pasteIdentityKey?.let {
                delete(pasteIdentityKey)
            }
        }
    }

    override fun identity(appInstanceId: String): ByteArray? {
        return realm.query(PasteIdentityKey::class, "appInstanceId == $0", appInstanceId)
            .first()
            .find()?.serialized
    }

    override fun loadPreKey(id: Int): ByteArray? {
        return realm.query(PastePreKey::class, "id == $0", id)
            .first()
            .find()?.serialized
    }

    override fun storePreKey(
        id: Int,
        serialized: ByteArray,
    ) {
        realm.writeBlocking {
            val newPastePreKey =
                PastePreKey().apply {
                    this.id = id
                    this.serialized = serialized
                }
            copyToRealm(newPastePreKey, updatePolicy = UpdatePolicy.ALL)
        }
    }

    override fun removePreKey(id: Int) {
        realm.writeBlocking {
            val pastePreKey = query(PastePreKey::class, "id == $0", id).first().find()
            pastePreKey?.let {
                delete(pastePreKey)
            }
        }
    }

    override fun loadSession(appInstanceId: String): ByteArray? {
        return realm.query(PasteSession::class, "appInstanceId == $0", appInstanceId)
            .first()
            .find()?.sessionRecord
    }

    override fun loadExistingSessions(): List<ByteArray> {
        return realm.query(PasteSession::class).find().map { it.sessionRecord }
    }

    override fun storeSession(
        appInstanceId: String,
        sessionRecord: ByteArray,
    ) {
        realm.writeBlocking {
            val newPasteSession =
                PasteSession().apply {
                    this.appInstanceId = appInstanceId
                    this.sessionRecord = sessionRecord
                }
            copyToRealm(newPasteSession, updatePolicy = UpdatePolicy.ALL)
        }
    }

    override fun containSession(appInstanceId: String): Boolean {
        return realm.query(PasteSession::class, "appInstanceId == $0", appInstanceId)
            .first()
            .find() != null
    }

    override fun deleteSession(appInstanceId: String) {
        realm.writeBlocking {
            val pasteSession = query(PasteSession::class, "appInstanceId == $0", appInstanceId).first().find()
            pasteSession?.let {
                delete(it)
            }
        }
    }

    override fun deleteAllSession() {
        realm.writeBlocking {
            val pasteSessions = query(PasteSession::class).find()
            delete(pasteSessions)
        }
    }

    override fun loadSignedPreKey(id: Int): ByteArray? {
        return realm.query(PasteSignedPreKey::class, "id == $0", id)
            .first()
            .find()?.serialized
    }

    override fun loadSignedPreKeys(): List<ByteArray> {
        return realm.query(PasteSignedPreKey::class).find().map { it.serialized }
    }

    override fun storeSignedPreKey(
        id: Int,
        serialized: ByteArray,
    ) {
        realm.writeBlocking {
            val newPasteSignedPreKey =
                PasteSignedPreKey().apply {
                    this.id = id
                    this.serialized = serialized
                }
            copyToRealm(newPasteSignedPreKey, updatePolicy = UpdatePolicy.ALL)
        }
    }

    override fun containsSignedPreKey(id: Int): Boolean {
        return realm.query(PasteSignedPreKey::class, "id == $0", id)
            .first()
            .find() != null
    }

    override fun removeSignedPreKey(id: Int) {
        realm.writeBlocking {
            val pasteSignedPreKey = query(PasteSignedPreKey::class, "id == $0", id).first().find()
            pasteSignedPreKey?.let {
                delete(it)
            }
        }
    }
}
