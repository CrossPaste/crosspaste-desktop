package com.clipevery.dao

import com.clipevery.Database
import org.signal.libsignal.protocol.state.SignedPreKeyStore

class DesktopGlobalSignedPreKeyStore(private val database: Database): GlobalSignedPreKeyStore {

    private val signedPreKeyStores = mutableMapOf<String, SignedPreKeyStore>()

    init {
        database.signedPreKeyQueries.selectDistinctAppInstanceIds().executeAsList().forEach {
            signedPreKeyStores[it] = DesktopSignedPreKeyStore(it, database)
        }
    }

    override fun computeIfAbsentSignedPreKeyStore(appInstanceId: String): SignedPreKeyStore {
        return signedPreKeyStores.computeIfAbsent(appInstanceId) {
            DesktopSignedPreKeyStore(it, database)
        }
    }
}
