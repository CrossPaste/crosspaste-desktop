package com.clipevery.dao

import org.signal.libsignal.protocol.state.PreKeyStore

interface GlobalPreKeyStore {

    fun computeIfAbsentPreKeyStore(appInstanceId: String): PreKeyStore
}
