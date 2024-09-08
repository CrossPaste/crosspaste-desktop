package com.crosspaste.signal

import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.SignalProtocolStore

class DesktopSessionBuilder(
    private val signalProtocolStore: SignalProtocolStoreInterface,
    private val signalAddress: SignalAddress,
) : SessionBuilderInterface {

    override fun process(preKeyBundle: PreKeyBundleInterface) {
        preKeyBundle as DesktopPreKeyBundle
        signalProtocolStore as SignalProtocolStore
        val signalProtocolAddress = SignalProtocolAddress(signalAddress.name, signalAddress.deviceId)
        SessionBuilder(signalProtocolStore, signalProtocolAddress)
            .process(preKeyBundle.preKeyBundle)
    }
}
