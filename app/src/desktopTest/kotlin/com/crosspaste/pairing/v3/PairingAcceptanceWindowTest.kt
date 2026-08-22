package com.crosspaste.pairing.v3

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PairingAcceptanceWindowTest {

    private var now = 0L

    private fun window(maxProofFailures: Int = PairingV3.MAX_ACCEPTOR_PROOF_FAILURES) =
        PairingAcceptanceWindow(maxProofFailures = maxProofFailures, nowEpochMillis = { now })

    @Test
    fun testWindowLifecycle() {
        val window = window()

        assertFalse(window.isOpen())

        assertTrue(window.open(WindowOpenSource.LOCAL))
        assertTrue(window.isOpen())

        now += 5 * 60 * 1000L
        assertFalse(window.isOpen())

        assertTrue(window.open(WindowOpenSource.REMOTE))
        assertTrue(window.isOpen())
        window.close()
        assertFalse(window.isOpen())
    }

    @Test
    fun failureBudgetLocksTheWindowAndBlocksRemoteReopen() {
        val window = window(maxProofFailures = 3)
        assertTrue(window.open(WindowOpenSource.REMOTE))

        // Below the budget, the window stays usable.
        assertFalse(window.recordProofFailure())
        assertFalse(window.recordProofFailure())
        assertTrue(window.isOpen())

        // The failure that spends the budget locks and closes the window.
        assertTrue(window.recordProofFailure())
        assertTrue(window.locked.value)
        assertFalse(window.isOpen())

        // A remote request cannot re-open a locked window.
        assertFalse(window.open(WindowOpenSource.REMOTE))
        assertFalse(window.isOpen())
    }

    @Test
    fun localOpenClearsTheLockAndResetsTheBudget() {
        val window = window(maxProofFailures = 2)
        window.open(WindowOpenSource.REMOTE)
        window.recordProofFailure()
        assertTrue(window.recordProofFailure())
        assertTrue(window.locked.value)

        // A local gesture (Add Device screen) clears the lock and gives a fresh budget.
        assertTrue(window.open(WindowOpenSource.LOCAL))
        assertFalse(window.locked.value)
        assertTrue(window.isOpen())

        // The budget is genuinely reset: it takes the full count again to re-lock.
        assertFalse(window.recordProofFailure())
        assertTrue(window.recordProofFailure())
        assertTrue(window.locked.value)
    }

    @Test
    fun extendRenewsWithoutResettingTheBudget() {
        val window = window(maxProofFailures = 3)
        window.open(WindowOpenSource.LOCAL)
        window.recordProofFailure()
        window.recordProofFailure()

        // Renewal (the on-screen keep-alive loop) must not refill the guess budget:
        // one more failure still locks.
        now += 2 * 60 * 1000L
        window.extend()
        assertTrue(window.isOpen())
        assertTrue(window.recordProofFailure())
        assertTrue(window.locked.value)
    }

    @Test
    fun extendIsANoOpWhileLocked() {
        val window = window(maxProofFailures = 1)
        window.open(WindowOpenSource.REMOTE)
        assertTrue(window.recordProofFailure())
        assertFalse(window.isOpen())

        window.extend()
        assertFalse(window.isOpen())
    }
}
