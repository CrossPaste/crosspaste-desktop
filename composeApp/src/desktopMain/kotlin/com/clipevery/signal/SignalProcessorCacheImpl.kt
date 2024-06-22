package com.clipevery.signal

import io.ktor.util.collections.*
import org.signal.libsignal.protocol.state.SignalProtocolStore

class SignalProcessorCacheImpl(private val signalProtocolStore: SignalProtocolStore) : SignalProcessorCache {

    private val sessionCipherCache: MutableMap<String, SignalMessageProcessor> = ConcurrentMap()

    override fun getSignalMessageProcessor(appInstanceId: String): SignalMessageProcessor {
        return sessionCipherCache.computeIfAbsent(appInstanceId) { key ->
            SignalMessageProcessorImpl(key, signalProtocolStore)
        }
    }

    override fun removeSignalMessageProcessor(appInstanceId: String) {
        sessionCipherCache.remove(appInstanceId)
    }
}
