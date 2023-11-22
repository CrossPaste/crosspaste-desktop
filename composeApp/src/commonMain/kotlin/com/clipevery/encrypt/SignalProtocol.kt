package com.clipevery.encrypt

import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyStore
import org.signal.libsignal.protocol.state.SessionStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyStore
import org.signal.libsignal.protocol.state.impl.InMemoryIdentityKeyStore
import org.signal.libsignal.protocol.state.impl.InMemoryPreKeyStore
import org.signal.libsignal.protocol.state.impl.InMemorySessionStore
import org.signal.libsignal.protocol.state.impl.InMemorySignedPreKeyStore

interface SignalProtocol {
    val identityKeyPair: IdentityKeyPair

    val registrationId: Int

    val preKeys: List<PreKeyRecord>

    val signedPreKey: SignedPreKeyRecord

    val sessionStore: SessionStore
        get() = InMemorySessionStore()

    val preKeyStore: PreKeyStore
        get() = InMemoryPreKeyStore()

    val signedPreKeyStore: SignedPreKeyStore
        get() = InMemorySignedPreKeyStore()

    val identityKeyStore: IdentityKeyStore
        get() = InMemoryIdentityKeyStore(identityKeyPair, registrationId)
}