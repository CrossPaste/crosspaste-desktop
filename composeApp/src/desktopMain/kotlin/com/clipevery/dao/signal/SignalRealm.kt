package com.clipevery.dao.signal

import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.util.Medium
import java.util.Random

class SignalRealm(private val realm: Realm): SignalDao {

    @Synchronized
    override fun generatePreKeyPair(): ClipPreKey {
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
    override fun generatesSignedPreKeyPair(privateKey: ECPrivateKey): ClipSignedPreKey {
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

    override fun saveIdentities(identityKeys: List<ClipIdentityKey>) {
        realm.writeBlocking {
            identityKeys.forEach { identityKey ->
                val newClipIdentityKey = ClipIdentityKey().apply {
                    this.appInstanceId = identityKey.appInstanceId
                    this.serialized = identityKey.serialized
                }
                copyToRealm(newClipIdentityKey, updatePolicy = UpdatePolicy.ALL)
            }
        }
    }

    override fun saveIdentity(appInstanceId: String, serialized: ByteArray): Boolean {
        return realm.writeBlocking {
            val clipIdentityKey = query(ClipIdentityKey::class, "appInstanceId == $0", appInstanceId).first().find()

            clipIdentityKey?.let {
                clipIdentityKey.serialized = serialized
                return@writeBlocking true
            }
            val newClipIdentityKey = ClipIdentityKey().apply {
                this.appInstanceId = appInstanceId
                this.serialized = serialized
            }
            copyToRealm(newClipIdentityKey)
            return@writeBlocking false
        }
    }

    override fun deleteIdentity(appInstanceId: String) {
        return realm.writeBlocking {
            val clipIdentityKey = query(ClipIdentityKey::class, "appInstanceId == $0", appInstanceId).first().find()
            clipIdentityKey?.let {
                delete(clipIdentityKey)
            }
        }
    }

    override fun identity(appInstanceId: String): ByteArray? {
        return realm.query(ClipIdentityKey::class, "appInstanceId == $0", appInstanceId)
            .first()
            .find()?.serialized
    }

    override fun loadPreKey(id: Int): ByteArray? {
        return realm.query(ClipPreKey::class, "id == $0", id)
            .first()
            .find()?.serialized
    }

    override fun storePreKey(id: Int, serialized: ByteArray) {
        realm.writeBlocking {
            val newClipPreKey = ClipPreKey().apply {
                this.id = id
                this.serialized = serialized
            }
            copyToRealm(newClipPreKey, updatePolicy = UpdatePolicy.ALL)
        }
    }

    override fun removePreKey(id: Int) {
        realm.writeBlocking {
            val clipPreKey = query(ClipPreKey::class, "id == $0", id).first().find()
            clipPreKey?.let {
                delete(clipPreKey)
            }
        }
    }

    override fun loadSession(appInstanceId: String): ByteArray? {
        return realm.query(ClipSession::class, "appInstanceId == $0", appInstanceId)
            .first()
            .find()?.sessionRecord
    }

    override fun loadExistingSessions(): List<ByteArray> {
        return realm.query(ClipSession::class).find().map { it.sessionRecord }
    }

    override fun storeSession(appInstanceId: String, sessionRecord: ByteArray) {
        realm.writeBlocking {
            val newClipSession = ClipSession().apply {
                this.appInstanceId = appInstanceId
                this.sessionRecord = sessionRecord
            }
            copyToRealm(newClipSession, updatePolicy = UpdatePolicy.ALL)
        }
    }

    override fun containSession(appInstanceId: String): Boolean {
        return realm.query(ClipSession::class, "appInstanceId == $0", appInstanceId)
            .first()
            .find() != null
    }

    override fun deleteSession(appInstanceId: String) {
        realm.writeBlocking {
            val clipSession = query(ClipSession::class, "appInstanceId == $0", appInstanceId).first().find()
            clipSession?.let {
                delete(it)
            }
        }
    }

    override fun deleteAllSession() {
        realm.writeBlocking {
            val clipSessions = query(ClipSession::class).find()
            delete(clipSessions)
        }
    }

    override fun loadSignedPreKey(id: Int): ByteArray? {
        return realm.query(ClipSignedPreKey::class, "id == $0", id)
            .first()
            .find()?.serialized
    }

    override fun loadSignedPreKeys(): List<ByteArray> {
        return realm.query(ClipSignedPreKey::class).find().map { it.serialized }
    }

    override fun storeSignedPreKey(id: Int, serialized: ByteArray) {
        realm.writeBlocking {
            val newClipSignedPreKey = ClipSignedPreKey().apply {
                this.id = id
                this.serialized = serialized
            }
            copyToRealm(newClipSignedPreKey, updatePolicy = UpdatePolicy.ALL)
        }
    }

    override fun containsSignedPreKey(id: Int): Boolean {
        return realm.query(ClipSignedPreKey::class, "id == $0", id)
            .first()
            .find() != null
    }

    override fun removeSignedPreKey(id: Int) {
        realm.writeBlocking {
            val clipSignedPreKey = query(ClipSignedPreKey::class, "id == $0", id).first().find()
            clipSignedPreKey?.let {
                delete(it)
            }
        }
    }
}
