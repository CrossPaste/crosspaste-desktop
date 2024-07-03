package com.crosspaste.signal

import com.crosspaste.dao.signal.SignalDao
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyStore

class DesktopSignedPreKeyStore(private val signalDao: SignalDao) : SignedPreKeyStore {
    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        signalDao.loadSignedPreKey(signedPreKeyId)?.let {
            return SignedPreKeyRecord(it)
        } ?: throw InvalidKeyIdException("No such signedPreKeyId: $signedPreKeyId")
    }

    override fun loadSignedPreKeys(): MutableList<SignedPreKeyRecord> {
        return signalDao.loadSignedPreKeys().map { SignedPreKeyRecord(it) }.toMutableList()
    }

    override fun storeSignedPreKey(
        signedPreKeyId: Int,
        record: SignedPreKeyRecord,
    ) {
        signalDao.storeSignedPreKey(signedPreKeyId, record.serialize())
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean {
        return signalDao.containsSignedPreKey(signedPreKeyId)
    }

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        signalDao.removeSignedPreKey(signedPreKeyId)
    }
}
