package com.clipevery.signal

import com.clipevery.dao.signal.SignalRealm
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyStore

class DesktopPreKeyStore(private val signalRealm: SignalRealm): PreKeyStore {

    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        signalRealm.loadPreKey(preKeyId)?.let {
            return PreKeyRecord(it)
        } ?: throw InvalidKeyIdException("No such preKeyId: $preKeyId")
    }

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        signalRealm.storePreKey(preKeyId, record.serialize())
    }

    override fun containsPreKey(preKeyId: Int): Boolean {
        signalRealm.loadPreKey(preKeyId)?.let {
            return true
        } ?: return false
    }

    override fun removePreKey(keyId: Int) {
        signalRealm.removePreKey(keyId)
    }
}