package com.clipevery.dao.store

import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.state.impl.InMemorySignalProtocolStore
import org.signal.libsignal.protocol.util.KeyHelper

class TestInMemorySignalProtocolStore(
    identityKeyPair: IdentityKeyPair = generateIdentityKeyPair(),
    registrationId: Int = generateRegistrationId(),
) : InMemorySignalProtocolStore(identityKeyPair, registrationId)

private fun generateIdentityKeyPair(): IdentityKeyPair {
    val identityKeyPairKeys = Curve.generateKeyPair()
    return IdentityKeyPair(
        IdentityKey(identityKeyPairKeys.publicKey),
        identityKeyPairKeys.privateKey,
    )
}

private fun generateRegistrationId(): Int {
    return KeyHelper.generateRegistrationId(false)
}
