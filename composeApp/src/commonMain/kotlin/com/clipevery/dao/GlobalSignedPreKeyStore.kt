package com.clipevery.dao

import org.signal.libsignal.protocol.state.SignedPreKeyStore

interface GlobalSignedPreKeyStore {

    fun computeIfAbsentSignedPreKeyStore(appInstanceId: String): SignedPreKeyStore
}
