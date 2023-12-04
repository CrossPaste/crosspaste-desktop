package com.clipevery.dao

import com.clipevery.Database
import com.clipevery.data.PreKey
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyStore

class DesktopPreKeyStore(private val database: Database): PreKeyStore {

    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        val preKey: PreKey = database.preKeyQueries.selectById(preKeyId.toLong())
            .executeAsOneOrNull() ?: throw InvalidKeyIdException("No such preKeyId: $preKeyId")
        return PreKeyRecord(preKey.serialized)
    }

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        database.preKeyQueries.insert(preKeyId.toLong(), record.serialize())
    }

    override fun containsPreKey(preKeyId: Int): Boolean {
        return database.preKeyQueries.count(preKeyId.toLong()).executeAsOneOrNull()?.let {
            return it > 0
        } ?: false
    }

    override fun removePreKey(keyId: Int) {
        database.preKeyQueries.delete(keyId.toLong())
    }
}