package com.clipevery.encrypt

interface SignalProtocolFactory {

    fun createSignalProtocol(): SignalProtocolWithState
}