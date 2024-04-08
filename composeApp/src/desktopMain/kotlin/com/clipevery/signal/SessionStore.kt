package com.clipevery.signal

import com.clipevery.dao.signal.SignalDao
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SessionStore

class DesktopSessionStore(private val signalDao: SignalDao) : SessionStore {

    override fun loadSession(address: SignalProtocolAddress): SessionRecord? {
        return signalDao.loadSession(address.name)?.let {
            return SessionRecord(it)
        }
    }

    override fun loadExistingSessions(addresses: MutableList<SignalProtocolAddress>?): MutableList<SessionRecord> {
        if (addresses!!.isEmpty()) {
            return mutableListOf()
        }
        return signalDao.loadExistingSessions().map { SessionRecord(it) }.toMutableList()
    }

    override fun getSubDeviceSessions(name: String): MutableList<Int> {
        signalDao.loadSession(name)?.let {
            return mutableListOf(1)
        } ?: return mutableListOf()
    }

    override fun storeSession(
        address: SignalProtocolAddress,
        record: SessionRecord,
    ) {
        signalDao.storeSession(address.name, record.serialize())
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean {
        return signalDao.containSession(address.name)
    }

    override fun deleteSession(address: SignalProtocolAddress) {
        return signalDao.deleteSession(address.name)
    }

    override fun deleteAllSessions(name: String) {
        signalDao.deleteAllSession()
    }
}
