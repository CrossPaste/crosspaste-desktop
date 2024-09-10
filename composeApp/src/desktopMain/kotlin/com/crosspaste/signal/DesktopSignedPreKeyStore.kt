package com.crosspaste.signal

import com.crosspaste.realm.signal.SignalRealm
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyStore

class DesktopSignedPreKeyStore(private val signalRealm: SignalRealm) : SignedPreKeyStore {
    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        signalRealm.loadSignedPreKey(signedPreKeyId)?.let {
            return SignedPreKeyRecord(it)
        } ?: throw InvalidKeyIdException("No such signedPreKeyId: $signedPreKeyId")
    }

    override fun loadSignedPreKeys(): MutableList<SignedPreKeyRecord> {
        return signalRealm.loadSignedPreKeys().map { SignedPreKeyRecord(it) }.toMutableList()
    }

    override fun storeSignedPreKey(
        signedPreKeyId: Int,
        record: SignedPreKeyRecord,
    ) {
        signalRealm.storeSignedPreKey(signedPreKeyId, record.serialize())
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean {
        return signalRealm.containsSignedPreKey(signedPreKeyId)
    }

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        signalRealm.removeSignedPreKey(signedPreKeyId)
    }
}
