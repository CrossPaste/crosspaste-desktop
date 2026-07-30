package com.crosspaste.sync

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
    fun `clear drops the record`() {
        val ledger = PendingExchangeLedger()
        ledger.record("a", 1L)
        ledger.clear("a")
        assertNull(ledger.current("a"))
        assertFalse(ledger.consume("a", 1L))
    }
}
