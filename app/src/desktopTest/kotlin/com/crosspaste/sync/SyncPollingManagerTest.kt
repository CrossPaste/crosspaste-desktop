package com.crosspaste.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives the polling schedule on virtual time: the manager reads the test scheduler's clock,
 * so each backoff step is asserted at its exact millisecond.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncPollingManagerTest {

    private class Harness(
        testScope: TestScope,
    ) {
        val scope = CoroutineScope(testScope.coroutineContext + Job())
        val manager = SyncPollingManager(scope) { testScope.testScheduler.currentTime }
        var actionCount = 0
    }

    // Advances to one millisecond before [atMs] (relative to now) and checks nothing ran,
    // then past it and checks exactly one more run.
    private fun TestScope.assertFiresAt(
        harness: Harness,
        atMs: Long,
    ) {
        val before = harness.actionCount
        advanceTimeBy(atMs - 1)
        runCurrent()
        assertEquals(before, harness.actionCount, "fired before ${atMs}ms")
        advanceTimeBy(1)
        runCurrent()
        assertEquals(before + 1, harness.actionCount, "did not fire at ${atMs}ms")
    }

    @Test
    fun noFailure_usesDefaultInterval() =
        runTest {
            val h = Harness(this)
            h.manager.startPollingResolve { h.actionCount++ }
            runCurrent()

            assertFiresAt(h, 60_000)
            h.scope.cancel()
        }

    @Test
    fun fail_triggersActionAtFirstBackoffStep() =
        runTest {
            val h = Harness(this)
            // 500 + 500 * 2^1
            h.manager.fail()
            h.manager.startPollingResolve { h.actionCount++ }
            runCurrent()

            assertFiresAt(h, 1_500)
            h.scope.cancel()
        }

    @Test
    fun multipleFails_increaseBackoff() =
        runTest {
            val h = Harness(this)
            // 500 + 500 * 2^3
            repeat(3) { h.manager.fail() }
            h.manager.startPollingResolve { h.actionCount++ }
            runCurrent()

            assertFiresAt(h, 4_500)
            h.scope.cancel()
        }

    @Test
    fun backoff_isCappedNearDefaultInterval() =
        runTest {
            val h = Harness(this)
            // 500 * 2^7 exceeds the 60s default, so the step is capped at 500 + 60_000
            repeat(7) { h.manager.fail() }
            h.manager.startPollingResolve { h.actionCount++ }
            runCurrent()

            assertFiresAt(h, 60_500)
            h.scope.cancel()
        }

    @Test
    fun reset_afterFails_restoresDefaultInterval() =
        runTest {
            val h = Harness(this)
            repeat(5) { h.manager.fail() }
            h.manager.reset()
            h.manager.startPollingResolve { h.actionCount++ }
            runCurrent()

            assertFiresAt(h, 60_000)
            assertEquals(0, h.manager.currentFailCount)
            h.scope.cancel()
        }

    @Test
    fun fail_afterReset_restartsBackoffFromFirstStep() =
        runTest {
            val h = Harness(this)
            repeat(3) { h.manager.fail() }
            h.manager.reset()
            h.manager.fail()
            h.manager.startPollingResolve { h.actionCount++ }
            runCurrent()

            assertFiresAt(h, 1_500)
            h.scope.cancel()
        }

    @Test
    fun fail_whileWaiting_pullsNextRunForward() =
        runTest {
            val h = Harness(this)
            h.manager.startPollingResolve { h.actionCount++ }
            runCurrent()
            advanceTimeBy(10_000)

            // Waiting on the 60s default; a failure reschedules from now
            h.manager.fail()
            assertFiresAt(h, 1_500)
            h.scope.cancel()
        }

    @Test
    fun cancelJob_stopsPolling() =
        runTest {
            val h = Harness(this)
            h.manager.fail()
            val job = h.manager.startPollingResolve { h.actionCount++ }
            runCurrent()
            assertFiresAt(h, 1_500)

            job.cancel()
            advanceTimeBy(120_000)
            runCurrent()

            assertEquals(1, h.actionCount)
            h.scope.cancel()
        }

    @Test
    fun scopeCancel_stopsPolling() =
        runTest {
            val h = Harness(this)
            h.manager.fail()
            h.manager.startPollingResolve { h.actionCount++ }
            runCurrent()
            assertFiresAt(h, 1_500)

            h.scope.cancel()
            advanceTimeBy(120_000)
            runCurrent()

            assertEquals(1, h.actionCount)
        }

    @Test
    fun startPollingResolve_actionExceptionDoesNotStopPolling() =
        runTest {
            val h = Harness(this)
            h.manager.fail()
            h.manager.startPollingResolve {
                h.actionCount++
                if (h.actionCount == 1) throw RuntimeException("test error")
            }
            runCurrent()
            assertFiresAt(h, 1_500)

            // The fail count still stands, so the next run is one backoff step later
            assertFiresAt(h, 1_500)
            h.scope.cancel()
        }

    @Test
    fun startPollingResolve_actionCancellationException_stopsPolling() =
        runTest {
            // Regression guard: if the action raises CancellationException, the polling
            // loop must terminate instead of swallowing it and spinning (which fed the
            // resolver infinite-loop).
            val h = Harness(this)
            h.manager.fail()
            val job =
                h.manager.startPollingResolve {
                    h.actionCount++
                    throw CancellationException("simulated cancellation")
                }
            runCurrent()
            advanceTimeBy(120_000)
            runCurrent()

            assertTrue(job.isCompleted, "Polling job should complete after cancellation")
            assertEquals(1, h.actionCount, "Action must not run again after cancellation")
            h.scope.cancel()
        }
}
