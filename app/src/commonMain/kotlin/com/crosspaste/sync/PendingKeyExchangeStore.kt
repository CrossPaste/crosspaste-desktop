package com.crosspaste.sync

import io.ktor.util.collections.ConcurrentMap

data class PendingKeyExchange(
    val signPublicKey: ByteArray,
    val cryptPublicKey: ByteArray,
    val sas: Int,
    val timestamp: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PendingKeyExchange) return false
        if (!signPublicKey.contentEquals(other.signPublicKey)) return false
        if (!cryptPublicKey.contentEquals(other.cryptPublicKey)) return false
        if (sas != other.sas) return false
        if (timestamp != other.timestamp) return false
        return true
    }

    override fun hashCode(): Int {
        var result = signPublicKey.contentHashCode()
        result = 31 * result + cryptPublicKey.contentHashCode()
        result = 31 * result + sas
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

class PendingKeyExchangeStore {

    private val store: MutableMap<String, PendingKeyExchange> = ConcurrentMap()

    companion object {
        private const val TTL_MS = 60_000L
    }

    fun put(
        appInstanceId: String,
        exchange: PendingKeyExchange,
    ) {
        store[appInstanceId] = exchange
    }

    fun get(appInstanceId: String): PendingKeyExchange? {
        val exchange = store[appInstanceId] ?: return null
        val now =
            com.crosspaste.utils.DateUtils
                .nowEpochMilliseconds()
        return if (now - exchange.timestamp > TTL_MS) {
            store.remove(appInstanceId)
            null
        } else {
            exchange
        }
    }

    fun remove(appInstanceId: String) {
        store.remove(appInstanceId)
    }
}
