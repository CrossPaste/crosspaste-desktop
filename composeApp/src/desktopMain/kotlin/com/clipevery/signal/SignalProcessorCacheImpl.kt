package com.clipevery.signal

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import org.signal.libsignal.protocol.state.SignalProtocolStore

class SignalProcessorCacheImpl(private val signalProtocolStore: SignalProtocolStore) : SignalProcessorCache {

    private val sessionCipherCache: LoadingCache<String, SignalMessageProcessor> =
        CacheBuilder.newBuilder()
            .build(
                object : CacheLoader<String, SignalMessageProcessor>() {
                    override fun load(key: String): SignalMessageProcessor {
                        return SignalMessageProcessorImpl(key, signalProtocolStore)
                    }
                },
            )

    override fun getSignalMessageProcessor(appInstanceId: String): SignalMessageProcessor {
        return sessionCipherCache.get(appInstanceId)
    }

    override fun removeSignalMessageProcessor(appInstanceId: String) {
        sessionCipherCache.invalidate(appInstanceId)
    }
}
