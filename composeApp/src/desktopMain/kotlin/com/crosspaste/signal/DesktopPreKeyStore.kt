package com.crosspaste.signal

import com.crosspaste.dao.signal.SignalDao
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyStore

class DesktopPreKeyStore(private val signalDao: SignalDao) : PreKeyStore {

    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        signalDao.loadPreKey(preKeyId)?.let {
            return PreKeyRecord(it)
        } ?: throw InvalidKeyIdException("No such preKeyId: $preKeyId")
    }

    override fun storePreKey(
        preKeyId: Int,
        record: PreKeyRecord,
    ) {
        signalDao.storePreKey(preKeyId, record.serialize())
    }

    override fun containsPreKey(preKeyId: Int): Boolean {
        signalDao.loadPreKey(preKeyId)?.let {
            return true
        } ?: return false
    }

    override fun removePreKey(keyId: Int) {
        signalDao.removePreKey(keyId)
    }
}
