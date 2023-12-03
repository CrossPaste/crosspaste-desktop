package com.clipevery.presist.data

import com.clipevery.Database
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SessionStore

class DesktopSessionStore(private val database: Database): SessionStore {

    override fun loadSession(address: SignalProtocolAddress): SessionRecord? {
        return database.syncQueries.selectSessionRecord(address.name).executeAsOneOrNull()?.let { selectSessionRecord ->
            val sessionRecord: ByteArray? = selectSessionRecord.sessionRecord
            return sessionRecord?.let {
                return SessionRecord(it)
            }
        }
    }

    override fun loadExistingSessions(addresses: MutableList<SignalProtocolAddress>?): MutableList<SessionRecord> {
        if (addresses!!.isEmpty()) {
            return mutableListOf()
        }
        database.syncQueries.selectSessionRecords(addresses.map { it.name }).executeAsList().let { sessionRecords ->
            return sessionRecords.mapNotNull { it -> it.sessionRecord?.let { SessionRecord(it) } }.toMutableList()
        }
    }

    override fun getSubDeviceSessions(name: String): MutableList<Int> {
        database.syncQueries.selectSubDevice(name).executeAsOneOrNull()?.let {
            return mutableListOf(1)
        } ?: return mutableListOf()
    }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        database.syncQueries.updateSessionRecord(record.serialize(), address.name)
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean {
        return database.syncQueries.count(address.name).executeAsOneOrNull()?.let {
            return it > 0
        } ?: false
    }

    override fun deleteSession(address: SignalProtocolAddress) {
        database.syncQueries.delete(address.name)
    }

    override fun deleteAllSessions(name: String) {
        database.syncQueries.delete(name)
    }
}