package com.clipevery.signal

import com.clipevery.dao.signal.SignalRealm
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SessionStore

class DesktopSessionStore(private val signalRealm: SignalRealm): SessionStore {

    override fun loadSession(address: SignalProtocolAddress): SessionRecord? {
        return signalRealm.loadSession(address.name)?.let {
            return SessionRecord(it)
        }
    }

    override fun loadExistingSessions(addresses: MutableList<SignalProtocolAddress>?): MutableList<SessionRecord> {
        if (addresses!!.isEmpty()) {
            return mutableListOf()
        }
        return signalRealm.loadExistingSessions().map { SessionRecord(it) }.toMutableList()
    }

    override fun getSubDeviceSessions(name: String): MutableList<Int> {
        signalRealm.loadSession(name)?.let {
            return mutableListOf(1)
        } ?: return mutableListOf()
    }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        signalRealm.storeSession(address.name, record.serialize())
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean {
        return signalRealm.containSession(address.name)
    }

    override fun deleteSession(address: SignalProtocolAddress) {
        return signalRealm.deleteSession(address.name)
    }

    override fun deleteAllSessions(name: String) {
       signalRealm.deleteAllSession()
    }
}