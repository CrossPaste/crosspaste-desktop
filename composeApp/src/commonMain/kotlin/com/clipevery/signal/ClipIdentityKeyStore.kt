package com.clipevery.signal

import org.signal.libsignal.protocol.state.IdentityKeyStore

interface ClipIdentityKeyStore: IdentityKeyStore {

    fun getPreKeyId(): Int

    fun getSignedPreKeyId(): Int

}
