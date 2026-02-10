package com.crosspaste.utils

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RetryUtilsTest {

    @Test
    fun `retry returns result on first success`() {
        val result = RetryUtils.retry(3) { "success" }
        assertEquals("success", result)
    }

    @Test
    fun `retry returns null when all attempts throw`() {
        val result =
            RetryUtils.retry<String>(3) {
                throw RuntimeException("fail")
            }
        assertNull(result)
    }

    @Test
    fun `retry succeeds on last attempt after earlier exceptions`() {
        var attempts = 0
        val result =
            RetryUtils.retry(3) { attempt ->
                attempts++
                if (attempt < 2) throw RuntimeException("fail")
                "recovered"
            }
        assertEquals("recovered", result)
        assertEquals(3, attempts)
    }

    @Test
    fun `retry returns null when block returns null every time`() {
        val result = RetryUtils.retry<String?>(3) { null }
        assertNull(result)
    }

    @Test
    fun `retry stops immediately on non-null result`() {
        var attempts = 0
        val result =
            RetryUtils.retry(5) {
                attempts++
                "done"
            }
        assertEquals("done", result)
        assertEquals(1, attempts)
    }

    @Test
    fun `retry passes attempt index to block`() {
        val indices = mutableListOf<Int>()
        RetryUtils.retry<String?>(3) { attempt ->
            indices.add(attempt)
            null
        }
        assertEquals(listOf(0, 1, 2), indices)
    }

    @Test
    fun `retry with maxRetries 1 calls block once on exception`() {
        var attempts = 0
        val result =
            RetryUtils.retry<String>(1) {
                attempts++
                throw RuntimeException("fail")
            }
        assertNull(result)
        assertEquals(1, attempts)
    }

    // --- suspendRetry ---

    @Test
    fun `suspendRetry returns result on first success`() =
        runTest {
            val result = RetryUtils.suspendRetry(3) { "success" }
            assertEquals("success", result)
        }

    @Test
    fun `suspendRetry returns null when all attempts throw`() =
        runTest {
            val result =
                RetryUtils.suspendRetry<String>(3) {
                    throw RuntimeException("fail")
                }
            assertNull(result)
        }

    @Test
    fun `suspendRetry succeeds on second attempt after first exception`() =
        runTest {
            val result =
                RetryUtils.suspendRetry(3) { attempt ->
                    if (attempt == 0) throw RuntimeException("fail")
                    "recovered"
                }
            assertEquals("recovered", result)
        }

    @Test
    fun `suspendRetry returns null when block returns null every time`() =
        runTest {
            val result = RetryUtils.suspendRetry<String?>(3) { null }
            assertNull(result)
        }
}
