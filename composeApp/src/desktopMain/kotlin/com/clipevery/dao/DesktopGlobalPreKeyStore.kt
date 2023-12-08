package com.clipevery.dao

import com.clipevery.Database
import org.signal.libsignal.protocol.state.PreKeyStore

class DesktopGlobalPreKeyStore(private val database: Database): GlobalPreKeyStore {

    private val preKeyStores = mutableMapOf<String, PreKeyStore>()

    init {
        database.preKeyQueries.selectDistinctAppInstanceIds().executeAsList().forEach {
            preKeyStores[it] = DesktopPreKeyStore(it, database)
        }
    }

    override fun computeIfAbsentPreKeyStore(appInstanceId: String): PreKeyStore {
        return preKeyStores.computeIfAbsent(appInstanceId) {
            DesktopPreKeyStore(it, database)
        }
    }
}
