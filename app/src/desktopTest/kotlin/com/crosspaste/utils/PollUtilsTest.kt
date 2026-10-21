package com.crosspaste.utils

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class PollUtilsTest {
    @Test
    fun `returns as soon as the condition is met`() =
        runTest {
            var checks = 0

            val met =
                pollUntil(300.milliseconds, 10.milliseconds) {
                    ++checks == 3
                }

            assertTrue(met)
            assertEquals(3, checks)
            assertEquals(20L, testScheduler.currentTime)
        }

    @Test
    fun `returns without waiting when the condition already holds`() =
        runTest {
            var checks = 0

            val met =
                pollUntil(300.milliseconds, 10.milliseconds) {
                    checks++
                    true
                }

            assertTrue(met)
            assertEquals(1, checks)
            assertEquals(0L, testScheduler.currentTime)
        }

    @Test
    fun `gives up at the timeout when the condition is never met`() =
        runTest {
            var checks = 0

            val met =
                pollUntil(300.milliseconds, 10.milliseconds) {
                    checks++
                    false
                }

            assertFalse(met)
            assertEquals(300L, testScheduler.currentTime)
            // One check per poll interval until the timeout interrupts the wait.
            assertTrue(checks >= 30)
        }
}
