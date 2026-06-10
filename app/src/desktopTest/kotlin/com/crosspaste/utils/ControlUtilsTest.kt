package com.crosspaste.utils

import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ControlUtilsTest {

    private val controlUtils = getControlUtils()

    @Test
    fun `exponential backoff returns immediately on valid first result`() =
        runBlocking {
            var calls = 0
            val start = nowEpochMilliseconds()
            val result =
                controlUtils.exponentialBackoffUntilValid(
                    initTime = 100L,
                    maxTime = 1000L,
                    isValidResult = { it: Int -> it > 0 },
                ) {
                    calls++
                    42
                }
            assertEquals(42, result)
            assertEquals(1, calls)
            assertTrue(nowEpochMilliseconds() - start < 100L, "valid first result must not wait")
        }

    @Test
    fun `exponential backoff retries until result becomes valid`() =
        runBlocking {
            var calls = 0
            val result =
                controlUtils.exponentialBackoffUntilValid(
                    initTime = 5L,
                    maxTime = 1000L,
                    isValidResult = { it: Int -> it >= 3 },
                ) {
                    ++calls
                }
            assertEquals(3, result)
            assertEquals(3, calls)
        }

    @Test
    fun `linear backoff retries until result becomes valid`() =
        runBlocking {
            var calls = 0
            val result =
                controlUtils.linearBackoffUntilValid(
                    initTime = 5L,
                    maxTime = 1000L,
                    isValidResult = { it: Int -> it >= 4 },
                ) {
                    ++calls
                }
            assertEquals(4, result)
            assertEquals(4, calls)
        }

    @Test
    fun `exponential backoff stops within real elapsed budget`() =
        runBlocking {
            val maxTime = 150L
            val start = nowEpochMilliseconds()
            val result =
                controlUtils.exponentialBackoffUntilValid(
                    initTime = 10L,
                    maxTime = maxTime,
                    isValidResult = { it: Int -> false },
                ) {
                    -1
                }
            val elapsed = nowEpochMilliseconds() - start
            assertEquals(-1, result)
            assertTrue(elapsed >= maxTime, "budget should be exhausted before giving up, elapsed=$elapsed")
            // The final wait is clamped to the remaining budget, so the loop must
            // not overshoot by a whole exponential step (a generous CI slack only).
            assertTrue(elapsed < maxTime + 500L, "budget overshot, elapsed=$elapsed")
        }

    @Test
    fun `linear backoff stops within real elapsed budget`() =
        runBlocking {
            val maxTime = 150L
            val start = nowEpochMilliseconds()
            val result =
                controlUtils.linearBackoffUntilValid(
                    initTime = 10L,
                    maxTime = maxTime,
                    isValidResult = { it: Int -> false },
                ) {
                    -1
                }
            val elapsed = nowEpochMilliseconds() - start
            assertEquals(-1, result)
            assertTrue(elapsed >= maxTime, "budget should be exhausted before giving up, elapsed=$elapsed")
            assertTrue(elapsed < maxTime + 500L, "budget overshot, elapsed=$elapsed")
        }

    @Test
    fun `backoff counts action time against the budget`() =
        runBlocking {
            val maxTime = 100L
            var calls = 0
            val start = nowEpochMilliseconds()
            val result =
                controlUtils.linearBackoffUntilValid(
                    initTime = 10L,
                    maxTime = maxTime,
                    isValidResult = { it: Int -> false },
                ) {
                    calls++
                    // A slow action must consume the budget instead of stretching it.
                    Thread.sleep(60L)
                    -1
                }
            val elapsed = nowEpochMilliseconds() - start
            assertEquals(-1, result)
            assertTrue(calls <= 3, "slow actions must shrink the retry count, calls=$calls")
            assertTrue(elapsed < maxTime + 60L + 500L, "budget overshot, elapsed=$elapsed")
        }
}
