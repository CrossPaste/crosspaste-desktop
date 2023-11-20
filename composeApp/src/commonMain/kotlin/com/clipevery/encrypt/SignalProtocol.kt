package com.clipevery.encrypt

import org.whispersystems.libsignal.IdentityKeyPair
import org.whispersystems.libsignal.state.IdentityKeyStore
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.PreKeyStore
import org.whispersystems.libsignal.state.SessionStore
import org.whispersystems.libsignal.state.SignedPreKeyRecord
import org.whispersystems.libsignal.state.SignedPreKeyStore
import org.whispersystems.libsignal.state.impl.InMemoryIdentityKeyStore
import org.whispersystems.libsignal.state.impl.InMemoryPreKeyStore
import org.whispersystems.libsignal.state.impl.InMemorySessionStore
import org.whispersystems.libsignal.state.impl.InMemorySignedPreKeyStore

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