package com.crosspaste.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PendingExchangeLedgerTest {

    @Test
    fun `nextGeneration is strictly monotonic even within one millisecond`() {
        val ledger = PendingExchangeLedger()
        var previous = ledger.nextGeneration()
        // Far more calls than can span distinct milliseconds on any machine.
        repeat(10_000) {
            val next = ledger.nextGeneration()
            assertTrue(next > previous)
            previous = next
        }
    }

    @Test
    fun `record and current round-trip per peer`() {
        val ledger = PendingExchangeLedger()
        ledger.record("a", 1L)
        ledger.record("b", 2L)
        assertEquals(1L, ledger.current("a"))
        assertEquals(2L, ledger.current("b"))
        assertNull(ledger.current("c"))
    }

    @Test
    fun `consume removes only the matching generation`() {
        val ledger = PendingExchangeLedger()
        ledger.record("a", 1L)

        assertFalse(ledger.consume("a", 2L))
        assertEquals(1L, ledger.current("a"))

        assertTrue(ledger.consume("a", 1L))
        assertNull(ledger.current("a"))

        assertFalse(ledger.consume("a", 1L))
    }

    @Test
    fun `successful consume drops the record`() {
        val ledger = PendingExchangeLedger()
        ledger.record("a", 1L)
        assertTrue(ledger.consume("a", 1L))
        assertNull(ledger.current("a"))
        assertFalse(ledger.consume("a", 1L))
    }

    @Test
    fun `concurrent stale consume never removes replacement generation`() =
        runBlocking {
            val ledger = PendingExchangeLedger()
            repeat(1_000) { attempt ->
                val oldGeneration = attempt.toLong() * 2
                val newGeneration = oldGeneration + 1
                ledger.record("a", oldGeneration)
                val start = CompletableDeferred<Unit>()
                val operations =
                    listOf(
                        async(Dispatchers.Default) {
                            start.await()
                            ledger.consume("a", oldGeneration)
                        },
                        async(Dispatchers.Default) {
                            start.await()
                            ledger.record("a", newGeneration)
                        },
                    )

                start.complete(Unit)
                operations.awaitAll()

                assertEquals(newGeneration, ledger.current("a"))
            }
        }
}
