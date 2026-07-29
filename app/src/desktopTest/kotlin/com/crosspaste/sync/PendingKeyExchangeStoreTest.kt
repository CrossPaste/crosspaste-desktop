package com.crosspaste.sync

import com.crosspaste.utils.DateUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PendingKeyExchangeStoreTest {

    private fun exchange(timestamp: Long): PendingKeyExchange =
        PendingKeyExchange(
            signPublicKey = byteArrayOf(1),
            cryptPublicKey = byteArrayOf(2),
            sas = 123456,
            timestamp = timestamp,
        )

    @Test
    fun putReportsWhetherAPreviousEntryWasReplaced() {
        val store = PendingKeyExchangeStore()
        val now = DateUtils.nowEpochMilliseconds()
        assertFalse(store.put("peer", exchange(now)))
        // Replacing a live entry — the peer holds an un-released refresh count.
        assertTrue(store.put("peer", exchange(now)))
        // Replacing an EXPIRED entry also reports true: its count was never released.
        val store2 = PendingKeyExchangeStore()
        assertFalse(store2.put("peer", exchange(now - 120_000L)))
        assertTrue(store2.put("peer", exchange(now)))
    }

    @Test
    fun lookupDistinguishesLiveExpiredAndNone() {
        val store = PendingKeyExchangeStore()
        val now = DateUtils.nowEpochMilliseconds()

        assertIs<PendingKeyExchangeLookup.None>(store.lookup("peer"))

        store.put("peer", exchange(now))
        val live = store.lookup("peer")
        assertIs<PendingKeyExchangeLookup.Live>(live)
        assertEquals(123456, live.exchange.sas)

        store.put("stale", exchange(now - 120_000L))
        assertIs<PendingKeyExchangeLookup.Expired>(store.lookup("stale"))
        // Expired entries are evicted on lookup: a second lookup sees nothing.
        assertIs<PendingKeyExchangeLookup.None>(store.lookup("stale"))
    }

    @Test
    fun getReturnsOnlyLiveEntries() {
        val store = PendingKeyExchangeStore()
        val now = DateUtils.nowEpochMilliseconds()
        store.put("stale", exchange(now - 120_000L))
        assertNull(store.get("stale"))
        store.put("peer", exchange(now))
        assertEquals(123456, store.get("peer")?.sas)
    }
}
