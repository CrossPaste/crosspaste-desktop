package com.clipevery.dao.store

import org.signal.libsignal.protocol.state.IdentityKeyStore

interface IdentityKeyStoreFactory {

    fun createIdentityKeyStore(): IdentityKeyStore
}
