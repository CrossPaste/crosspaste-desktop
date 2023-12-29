package com.clipevery.signal

import org.signal.libsignal.protocol.state.IdentityKeyStore

interface IdentityKeyStoreFactory {

    fun createIdentityKeyStore(): IdentityKeyStore
}
