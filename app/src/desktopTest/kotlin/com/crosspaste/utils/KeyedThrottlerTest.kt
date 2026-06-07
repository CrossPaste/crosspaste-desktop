package com.crosspaste.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyedThrottlerTest {

    @Test
    fun tryAcquire_firstCall_fires() {
        val throttler = KeyedThrottler<String>(1000L)
        assertTrue(throttler.tryAcquire("a", now = 0L))
    }

    @Test
    fun tryAcquire_withinInterval_suppressed() {
        val throttler = KeyedThrottler<String>(1000L)
        assertTrue(throttler.tryAcquire("a", now = 0L))
        assertFalse(throttler.tryAcquire("a", now = 500L))
        assertFalse(throttler.tryAcquire("a", now = 999L))
    }

    @Test
    fun tryAcquire_afterInterval_firesAgain() {
        val throttler = KeyedThrottler<String>(1000L)
        assertTrue(throttler.tryAcquire("a", now = 0L))
        assertTrue(throttler.tryAcquire("a", now = 1000L))
        assertFalse(throttler.tryAcquire("a", now = 1500L))
        assertTrue(throttler.tryAcquire("a", now = 2000L))
    }

    @Test
    fun tryAcquire_keysAreIndependent() {
        val throttler = KeyedThrottler<String>(1000L)
        assertTrue(throttler.tryAcquire("a", now = 0L))
        assertTrue(throttler.tryAcquire("b", now = 0L))
        assertFalse(throttler.tryAcquire("a", now = 100L))
        assertFalse(throttler.tryAcquire("b", now = 100L))
    }
}
