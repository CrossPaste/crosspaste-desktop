package com.crosspaste.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * SyncPollingManager uses real wall-clock time (nowEpochMilliseconds) for scheduling,
 * so these tests use runBlocking with real-time delays instead of runTest with virtual time.
 * The fail() backoff starts at 1500ms (500 + 500*2^1), keeping test times reasonable.
 */
class SyncPollingManagerTest {

    @Test
    fun fail_triggersActionSooner() =
        runBlocking {
            val childScope = CoroutineScope(coroutineContext + Job())
            val manager = SyncPollingManager(childScope)
            var actionCount = 0

            // After fail(), delay = 500 + 500*2 = 1500ms (much shorter than default 60s)
            manager.fail()

            val job =
                manager.startPollingResolve {
                    actionCount++
                }

            withTimeout(5.seconds) {
                while (actionCount == 0) {
                    delay(50)
                }
            }

            assertTrue(actionCount >= 1, "Action should execute after fail triggers shorter backoff")
            job.cancel()
            childScope.cancel()
        }

    @Test
    fun multipleFails_increaseBackoff() =
        runBlocking {
            val childScope = CoroutineScope(coroutineContext + Job())
            val manager = SyncPollingManager(childScope)

            // 3 fails -> delay = 500 + 500*2^3 = 4500ms
            manager.fail()
            manager.fail()
            manager.fail()

            var actionCount = 0
            val job =
                manager.startPollingResolve {
                    actionCount++
                }

            // At 1s, should not have fired yet (delay is ~4500ms)
            delay(1_000)
            val countAt1s = actionCount

            withTimeout(10.seconds) {
                while (actionCount == countAt1s) {
                    delay(50)
                }
            }

            assertTrue(actionCount > countAt1s, "Action should fire after sufficient time")
            job.cancel()
            childScope.cancel()
        }

    @Test
    fun reset_afterFails_restoresDefaultInterval() =
        runBlocking {
            val childScope = CoroutineScope(coroutineContext + Job())
            val manager = SyncPollingManager(childScope)

            // Fail several times to increase backoff
            manager.fail()
            manager.fail()
            manager.fail()
            manager.fail()
            manager.fail()

            // Reset should bring it back to default 60s poll interval
            manager.reset()

            var actionCount = 0
            val job =
                manager.startPollingResolve {
                    actionCount++
                }

            // After reset, default interval is 60s; within 3s action should NOT fire
            delay(3_000)
            assertTrue(actionCount == 0, "Action should not fire within 3s at default 60s interval")
            job.cancel()
            childScope.cancel()
        }

    @Test
    fun cancelJob_stopsPolling() =
        runBlocking {
            val childScope = CoroutineScope(coroutineContext + Job())
            val manager = SyncPollingManager(childScope)
            var actionCount = 0

            manager.fail() // Short delay so action fires quickly

            val job =
                manager.startPollingResolve {
                    actionCount++
                }

            withTimeout(5.seconds) {
                while (actionCount == 0) {
                    delay(50)
                }
            }
            val countBeforeCancel = actionCount

            job.cancel()
            delay(3_000)

            assertTrue(
                actionCount <= countBeforeCancel + 1,
                "Action count should not increase significantly after cancel",
            )
            childScope.cancel()
        }

    @Test
    fun fail_afterReset_usesNewBackoff() =
        runBlocking {
            val childScope = CoroutineScope(coroutineContext + Job())
            val manager = SyncPollingManager(childScope)

            // Fail then reset, then fail again
            manager.fail()
            manager.fail()
            manager.fail()
            manager.reset()
            manager.fail() // Back to first fail level: 500 + 500*2 = 1500ms

            var actionCount = 0
            val job =
                manager.startPollingResolve {
                    actionCount++
                }

            withTimeout(5.seconds) {
                while (actionCount == 0) {
                    delay(50)
                }
            }

            assertTrue(actionCount >= 1, "Action should fire at first-fail backoff level")
            job.cancel()
            childScope.cancel()
        }

    @Test
    fun startPollingResolve_actionExceptionDoesNotStopPolling() =
        runBlocking {
            val childScope = CoroutineScope(coroutineContext + Job())
            val manager = SyncPollingManager(childScope)
            var actionCount = 0

            manager.fail() // Use short backoff so tests run quickly

            val job =
                manager.startPollingResolve {
                    actionCount++
                    if (actionCount == 1) throw RuntimeException("test error")
                }

            // Wait for first execution
            withTimeout(5.seconds) {
                while (actionCount == 0) {
                    delay(50)
                }
            }
            val firstCount = actionCount

            // Fail again to get another short delay after the exception
            manager.fail()

            // Wait for second execution
            withTimeout(5.seconds) {
                while (actionCount <= firstCount) {
                    delay(50)
                }
            }

            assertTrue(actionCount > firstCount, "Polling should continue after exception")
            job.cancel()
            childScope.cancel()
        }

    @Test
    fun scopeCancel_stopsPolling() =
        runBlocking {
            val childScope = CoroutineScope(coroutineContext + Job())
            val manager = SyncPollingManager(childScope)
            var actionCount = 0

            manager.fail()

            manager.startPollingResolve {
                actionCount++
            }

            withTimeout(5.seconds) {
                while (actionCount == 0) {
                    delay(50)
                }
            }

            val countBefore = actionCount
            childScope.cancel()
            delay(3_000)

            assertTrue(
                actionCount <= countBefore + 1,
                "Polling should stop after scope cancellation",
            )
        }
}
