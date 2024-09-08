package com.crosspaste.signal

interface SessionBuilderFactory {

    fun createSessionBuilder(signalAddress: SignalAddress): SessionBuilderInterface
}
