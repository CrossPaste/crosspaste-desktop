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

/**
 * Lookup result distinguishing an expired entry from no entry at all: the
 * caller owns one token-refresh count per stored exchange, so it must know
 * whether an expired exchange was just evicted (its count needs releasing)
 * or nothing was ever stored.
 */
sealed interface PendingKeyExchangeLookup {
    data class Live(
        val exchange: PendingKeyExchange,
    ) : PendingKeyExchangeLookup

    data object Expired : PendingKeyExchangeLookup

    data object None : PendingKeyExchangeLookup
}

class PendingKeyExchangeStore {

    private val store: MutableMap<String, PendingKeyExchange> = ConcurrentMap()

    companion object {
        private const val TTL_MS = 60_000L
    }

    /**
     * Stores the peer's exchange. Returns true when it REPLACED a previous
     * entry (live or expired) — i.e. the peer already held an un-released
     * token-refresh count from an earlier exchange.
     */
    fun put(
        appInstanceId: String,
        exchange: PendingKeyExchange,
    ): Boolean = store.put(appInstanceId, exchange) != null

    fun get(appInstanceId: String): PendingKeyExchange? =
        (lookup(appInstanceId) as? PendingKeyExchangeLookup.Live)?.exchange

    /**
     * Pure lookup — expired entries are reported but NOT evicted here. Every
     * stored entry owns one token-refresh count and one pending verifier, and
     * only the unified release path (`remove` + verifier + stopRefresh, see
     * DefaultServerModule) may take an entry out, so ownership stays traceable.
     */
    fun lookup(appInstanceId: String): PendingKeyExchangeLookup {
        val exchange = store[appInstanceId] ?: return PendingKeyExchangeLookup.None
        val now =
            com.crosspaste.utils.DateUtils
                .nowEpochMilliseconds()
        return if (now - exchange.timestamp > TTL_MS) {
            PendingKeyExchangeLookup.Expired
        } else {
            PendingKeyExchangeLookup.Live(exchange)
        }
    }

    /** Removes the peer's entry, reporting whether one existed. */
    fun remove(appInstanceId: String): Boolean = store.remove(appInstanceId) != null
}
