package com.clipevery.encrypt

import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.libsignal.state.SessionRecord
import org.whispersystems.libsignal.state.SessionStore

class MacSessionStore: SessionStore {
    override fun loadSession(address: SignalProtocolAddress?): SessionRecord {
        TODO("Not yet implemented")
    }

    override fun getSubDeviceSessions(name: String?): MutableList<Int> {
        TODO("Not yet implemented")
    }

    override fun storeSession(address: SignalProtocolAddress?, record: SessionRecord?) {
        TODO("Not yet implemented")
    }

    override fun containsSession(address: SignalProtocolAddress?): Boolean {
        TODO("Not yet implemented")
    }

    override fun deleteSession(address: SignalProtocolAddress?) {
        TODO("Not yet implemented")
    }

    override fun deleteAllSessions(name: String?) {
        TODO("Not yet implemented")
    }
}