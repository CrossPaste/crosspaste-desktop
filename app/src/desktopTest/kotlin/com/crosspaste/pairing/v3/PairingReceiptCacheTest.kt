package com.crosspaste.pairing.v3

import com.crosspaste.dto.pairing.v3.PairingCommitAckV3
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PairingReceiptCacheTest {

    private var now = 0L

    private fun newCache(
        ttlMillis: Long = 600_000L,
        maxEntries: Int = 2,
    ) = PairingReceiptCache(
        ttlMillis = ttlMillis,
        maxEntries = maxEntries,
        nowEpochMillis = { now },
    )

    private fun ack(
        sessionId: String,
        marker: Byte = 2,
    ) = PairingCommitAckV3(
        sessionId = sessionId.encodeToByteArray(),
        transcriptHash = ByteArray(32) { 1 },
        receiptMac = ByteArray(32) { marker },
    )

    @Test
    fun testFirstWriteWinsForIdenticalRetries() =
        runTest {
            val cache = newCache()
            val original = ack("s1", marker = 2)

            assertIs<PairingReceiptCache.Record.Inserted>(cache.recordOrLookup("s1", "mac-1", original))

            // Identical retry with a freshly-built (different) ack object must
            // return the ORIGINAL receipt, not the new one
            val retryAck = ack("s1", marker = 9)
            val hit = assertIs<PairingReceiptCache.Record.Hit>(cache.recordOrLookup("s1", "mac-1", retryAck))
            assertEquals(original, hit.ack)
        }

    @Test
    fun testConflictingCommitNeverOverwrites() =
        runTest {
            val cache = newCache()
            val original = ack("s1")
            cache.recordOrLookup("s1", "mac-1", original)

            assertIs<PairingReceiptCache.Record.Conflict>(
                cache.recordOrLookup("s1", "mac-other", ack("s1", marker = 9)),
            )

            // The original entry is intact after the conflict attempt
            val hit = assertIs<PairingReceiptCache.Lookup.Hit>(cache.lookup("s1", "mac-1"))
            assertEquals(original, hit.ack)
        }

    @Test
    fun testCachedReceiptIsIsolatedFromCallerMutation() =
        runTest {
            val cache = newCache()
            val original = ack("s1", marker = 2)
            val expected = ack("s1", marker = 2)

            val inserted =
                assertIs<PairingReceiptCache.Record.Inserted>(
                    cache.recordOrLookup("s1", "mac-1", original),
                )
            original.sessionId.fill(0)
            original.transcriptHash.fill(0)
            original.receiptMac.fill(0)
            inserted.ack.receiptMac.fill(9)

            val firstHit = assertIs<PairingReceiptCache.Lookup.Hit>(cache.lookup("s1", "mac-1"))
            assertEquals(expected, firstHit.ack)
            firstHit.ack.receiptMac.fill(8)

            val secondHit = assertIs<PairingReceiptCache.Lookup.Hit>(cache.lookup("s1", "mac-1"))
            assertContentEquals(expected.receiptMac, secondHit.ack.receiptMac)
        }

    @Test
    fun testLookupResolvesRetriesAndConflicts() =
        runTest {
            val cache = newCache()
            cache.recordOrLookup("s1", "mac-1", ack("s1"))

            assertIs<PairingReceiptCache.Lookup.Hit>(cache.lookup("s1", "mac-1"))
            assertIs<PairingReceiptCache.Lookup.Conflict>(cache.lookup("s1", "mac-other"))
            assertIs<PairingReceiptCache.Lookup.Miss>(cache.lookup("s2", "mac-1"))
        }

    @Test
    fun testTtlEviction() =
        runTest {
            val cache = newCache(ttlMillis = 1_000L)
            cache.recordOrLookup("s1", "mac-1", ack("s1"))

            now += 1_001L
            assertIs<PairingReceiptCache.Lookup.Miss>(cache.lookup("s1", "mac-1"))
        }

    @Test
    fun testBoundedCapacityEvictsOldestOnlyForNewSessions() =
        runTest {
            val cache = newCache(maxEntries = 2)
            cache.recordOrLookup("s1", "mac-1", ack("s1"))
            now += 1
            cache.recordOrLookup("s2", "mac-2", ack("s2"))
            now += 1
            cache.recordOrLookup("s3", "mac-3", ack("s3"))

            assertIs<PairingReceiptCache.Lookup.Miss>(cache.lookup("s1", "mac-1"))
            assertIs<PairingReceiptCache.Lookup.Hit>(cache.lookup("s2", "mac-2"))
            assertIs<PairingReceiptCache.Lookup.Hit>(cache.lookup("s3", "mac-3"))
        }
}
