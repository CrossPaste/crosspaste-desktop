package com.crosspaste.signal

import io.ktor.util.collections.*

class SignalProcessorCacheImpl(private val signalProtocolStore: SignalProtocolStoreInterface) : SignalProcessorCache {

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
