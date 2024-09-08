package com.crosspaste.signal

class DesktopSessionBuilderFactory(
    private val signalProtocolStoreInterface: SignalProtocolStoreInterface,
) : SessionBuilderFactory {
    override fun createSessionBuilder(signalAddress: SignalAddress): SessionBuilderInterface {
        return DesktopSessionBuilder(signalProtocolStoreInterface, signalAddress)
    }
}
