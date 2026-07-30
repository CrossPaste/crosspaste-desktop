package com.crosspaste.sync

import com.crosspaste.utils.DateUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PendingKeyExchangeStoreTest {

    private fun exchange(
        timestamp: Long,
        generation: Long = timestamp,
    ): PendingKeyExchange =
        PendingKeyExchange(
            signPublicKey = byteArrayOf(1),
            cryptPublicKey = byteArrayOf(2),
            sas = 123456,
            timestamp = timestamp,
            generation = generation,
        )

    @Test
    fun generationMatchesComparesTheStoredInitiatorMarker() {
        val store = PendingKeyExchangeStore()
        val now = DateUtils.nowEpochMilliseconds()
        assertFalse(store.generationMatches("peer", 42L))

        store.put("peer", exchange(now, generation = 42L))
        assertTrue(store.generationMatches("peer", 42L))
        assertFalse(store.generationMatches("peer", 43L))

        // An EXPIRED entry still owns its refresh count, so its generation
        // remains matchable until the release path removes it.
        val store2 = PendingKeyExchangeStore()
        store2.put("stale", exchange(now - 120_000L, generation = 42L))
        assertTrue(store2.generationMatches("stale", 42L))
    }

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
        // lookup is pure: the expired entry stays until the release path
        // removes it, so its refresh-count ownership remains traceable.
        assertIs<PendingKeyExchangeLookup.Expired>(store.lookup("stale"))
        assertTrue(store.remove("stale"))
        assertIs<PendingKeyExchangeLookup.None>(store.lookup("stale"))
        assertFalse(store.remove("stale"))
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
