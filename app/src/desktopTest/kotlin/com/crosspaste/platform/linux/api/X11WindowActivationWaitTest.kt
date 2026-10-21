package com.crosspaste.platform.linux.api

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class X11WindowActivationWaitTest {
    @Test
    fun `returns as soon as the window is reported active`() =
        runTest {
            var checks = 0

            val activated =
                awaitWindowActive(300.milliseconds, 10.milliseconds) {
                    ++checks == 3
                }

            assertTrue(activated)
            assertEquals(3, checks)
            assertEquals(20L, testScheduler.currentTime)
        }

    @Test
    fun `returns without waiting when the window is already active`() =
        runTest {
            var checks = 0

            val activated =
                awaitWindowActive(300.milliseconds, 10.milliseconds) {
                    checks++
                    true
                }

            assertTrue(activated)
            assertEquals(1, checks)
            assertEquals(0L, testScheduler.currentTime)
        }

    @Test
    fun `gives up at the timeout when the window never becomes active`() =
        runTest {
            var checks = 0

            val activated =
                awaitWindowActive(300.milliseconds, 10.milliseconds) {
                    checks++
                    false
                }

            assertFalse(activated)
            assertEquals(300L, testScheduler.currentTime)
            // One check per poll interval until the timeout interrupts the wait.
            assertTrue(checks >= 30)
        }
}
