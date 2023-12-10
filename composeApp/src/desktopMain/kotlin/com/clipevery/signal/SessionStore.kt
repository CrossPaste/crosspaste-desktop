package com.clipevery.signal

import com.clipevery.Database
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SessionStore

class DesktopSessionStore(private val database: Database): SessionStore {

    override fun loadSession(address: SignalProtocolAddress): SessionRecord? {
        return database.sessionQueries.selectSessionRecord(address.name).executeAsOneOrNull()?.let {
            return SessionRecord(it)
        }
    }

    override fun loadExistingSessions(addresses: MutableList<SignalProtocolAddress>?): MutableList<SessionRecord> {
        if (addresses!!.isEmpty()) {
            return mutableListOf()
        }
        database.sessionQueries.selectSessionRecords(addresses.map { it.name }).executeAsList().let { sessionRecords ->
            return sessionRecords.map { SessionRecord(it) }.toMutableList()
        }
    }

    override fun getSubDeviceSessions(name: String): MutableList<Int> {
        database.sessionQueries.selectSubDevice(name).executeAsOneOrNull()?.let {
            return mutableListOf(1)
        } ?: return mutableListOf()
    }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        database.sessionQueries.updateSessionRecord(record.serialize(), address.name)
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean {
        return database.sessionQueries.count(address.name).executeAsOneOrNull()?.let {
            return it > 0
        } ?: false
    }

    override fun deleteSession(address: SignalProtocolAddress) {
        database.sessionQueries.delete(address.name)
    }

    override fun deleteAllSessions(name: String) {
        database.sessionQueries.delete(name)
    }
}