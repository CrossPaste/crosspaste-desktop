package com.clipevery.dao.signal

import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.util.Medium
import java.util.Random

class SignalRealm(private val realm: Realm) {

    @Synchronized
    fun generatePreKeyPair(): ClipPreKey {
        val preKeyPair = Curve.generateKeyPair()
        val random = Random()
        var preKeyId: Int
        do {
            preKeyId = random.nextInt(Medium.MAX_VALUE)
        } while (existPreKey(preKeyId))
        val preKeyRecord = PreKeyRecord(preKeyId, preKeyPair)
        val serialize = preKeyRecord.serialize()
        storePreKey(preKeyId, serialize)
        return ClipPreKey(preKeyId, serialize)
    }

    @Synchronized
    fun generatesSignedPreKeyPair(privateKey: ECPrivateKey): ClipSignedPreKey {
        val random = Random()
        val signedPreKeyId = random.nextInt(Medium.MAX_VALUE)

        getSignedPreKey(signedPreKeyId)?.let { signedPreKey ->
            return signedPreKey
        } ?: run {
            val signedPreKeyPair = Curve.generateKeyPair()
            val signedPreKeySignature = Curve.calculateSignature(
                privateKey,
                signedPreKeyPair.publicKey.serialize()
            )
            val signedPreKeyRecord = SignedPreKeyRecord(
                signedPreKeyId, System.currentTimeMillis(), signedPreKeyPair, signedPreKeySignature
            )
            storeSignedPreKey(signedPreKeyId, signedPreKeyRecord.serialize())
            return ClipSignedPreKey(signedPreKeyId, signedPreKeyRecord.serialize())
        }
    }

    private fun existPreKey(preKeyId: Int): Boolean {
        return realm.query(ClipPreKey::class, "id == $0", preKeyId).first().find() != null
    }

    private fun getSignedPreKey(signedPreKeyId: Int): ClipSignedPreKey? {
        return realm.query(ClipSignedPreKey::class, "id == $0", signedPreKeyId).first().find()
    }

    fun saveIdentity(appInstanceId: String, serialized: ByteArray): Boolean {
        return realm.writeBlocking {
            val clipIdentityKey = realm.query(ClipIdentityKey::class, "appInstanceId == $0", appInstanceId).first().find()

            clipIdentityKey?.let {
                findLatest(it)?.let { key ->
                    key.serialized = serialized
                    return@writeBlocking true
                }
            }
            val newClipIdentityKey = ClipIdentityKey().apply {
                this.appInstanceId = appInstanceId
                this.serialized = serialized
            }
            copyToRealm(newClipIdentityKey)
            return@writeBlocking false
        }
    }

    fun identity(appInstanceId: String): ByteArray? {
        return realm.query(ClipIdentityKey::class, "appInstanceId == $0", appInstanceId)
            .first()
            .find()?.serialized
    }

    fun loadPreKey(id: Int): ByteArray? {
        return realm.query(ClipPreKey::class, "id == $0", id)
            .first()
            .find()?.serialized
    }

    fun storePreKey(id: Int, serialized: ByteArray) {
        realm.writeBlocking {
            val newClipPreKey = ClipPreKey().apply {
                this.id = id
                this.serialized = serialized
            }
            copyToRealm(newClipPreKey, updatePolicy = UpdatePolicy.ALL)
        }
    }

    fun removePreKey(id: Int) {
        realm.writeBlocking {
            val clipPreKey = realm.query(ClipPreKey::class, "id == $0", id).first().find()
            clipPreKey?.let {
                findLatest(clipPreKey)?.let { key ->
                    delete(key)
                }
            }
        }
    }

    fun loadSession(appInstanceId: String): ByteArray? {
        return realm.query(ClipSession::class, "appInstanceId == $0", appInstanceId)
            .first()
            .find()?.sessionRecord
    }

    fun loadExistingSessions(): List<ByteArray> {
        return realm.query(ClipSession::class).find().map { it.sessionRecord }
    }

    fun storeSession(appInstanceId: String, sessionRecord: ByteArray) {
        realm.writeBlocking {
            val newClipSession = ClipSession().apply {
                this.appInstanceId = appInstanceId
                this.sessionRecord = sessionRecord
            }
            copyToRealm(newClipSession, updatePolicy = UpdatePolicy.ALL)
        }
    }

    fun containSession(appInstanceId: String): Boolean {
        return realm.query(ClipSession::class, "appInstanceId == $0", appInstanceId)
            .first()
            .find() != null
    }

    fun deleteSession(appInstanceId: String) {
        realm.writeBlocking {
            val clipSession = realm.query(ClipSession::class, "appInstanceId == $0", appInstanceId).first().find()
            clipSession?.let {
                findLatest(clipSession)?.let {
                    delete(it)
                }
            }
        }
    }

    fun deleteAllSession() {
        realm.writeBlocking {
            val clipSessions = realm.query(ClipSession::class).find()
            delete(clipSessions)
        }
    }

    fun loadSignedPreKey(id: Int): ByteArray? {
        return realm.query(ClipSignedPreKey::class, "id == $0", id)
            .first()
            .find()?.serialized
    }

    fun loadSignedPreKeys(): List<ByteArray> {
        return realm.query(ClipSignedPreKey::class).find().map { it.serialized }
    }

    fun storeSignedPreKey(id: Int, serialized: ByteArray) {
        realm.writeBlocking {
            val newClipSignedPreKey = ClipSignedPreKey().apply {
                this.id = id
                this.serialized = serialized
            }
            copyToRealm(newClipSignedPreKey, updatePolicy = UpdatePolicy.ALL)
        }
    }

    fun containsSignedPreKey(id: Int): Boolean {
        return realm.query(ClipSignedPreKey::class, "id == $0", id)
            .first()
            .find() != null
    }

    fun removeSignedPreKey(id: Int) {
        realm.writeBlocking {
            val clipSignedPreKey = realm.query(ClipSignedPreKey::class, "id == $0", id).first().find()
            clipSignedPreKey?.let {
                delete(it)
            }
        }
    }
}
