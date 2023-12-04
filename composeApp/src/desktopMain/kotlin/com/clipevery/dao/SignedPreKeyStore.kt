package com.clipevery.dao

import com.clipevery.Database
import com.clipevery.data.SignedPreKey
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyStore

class DesktopSignedPreKeyStore(private val database: Database): SignedPreKeyStore {
    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        val signedPreKey: SignedPreKey = database.signedPreKeyQueries.selectById(signedPreKeyId.toLong())
            .executeAsOneOrNull()?.let {
                return SignedPreKeyRecord(it.serialized)
            } ?: throw InvalidKeyIdException("No such signedPreKeyId: $signedPreKeyId")
        return SignedPreKeyRecord(signedPreKey.serialized)
    }

    override fun loadSignedPreKeys(): MutableList<SignedPreKeyRecord> {
        database.signedPreKeyQueries.selectAll().executeAsList().let { signedPreKeys ->
            return signedPreKeys.map { SignedPreKeyRecord(it.serialized) }.toMutableList()
        }
    }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        database.signedPreKeyQueries.insert(signedPreKeyId.toLong(), record.serialize())
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean {
        return database.signedPreKeyQueries.count(signedPreKeyId.toLong()).executeAsOneOrNull()?.let {
            return it > 0
        } ?: false
    }

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        database.signedPreKeyQueries.delete(signedPreKeyId.toLong())
    }
}