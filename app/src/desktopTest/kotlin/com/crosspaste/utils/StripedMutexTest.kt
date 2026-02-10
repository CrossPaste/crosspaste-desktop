package com.crosspaste.utils

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StripedMutexTest {

    @Test
    fun `withLock executes action and returns result`() =
        runTest {
            val mutex = StripedMutex()
            val result = mutex.withLock("key") { 42 }
            assertEquals(42, result)
        }

    @Test
    fun `same key serializes concurrent access`() =
        runTest {
            val mutex = StripedMutex()
            val log = mutableListOf<String>()

            val job1 =
                async {
                    mutex.withLock("sameKey") {
                        log.add("start1")
                        delay(50)
                        log.add("end1")
                    }
                }
            // Give job1 time to acquire the lock
            delay(10)
            val job2 =
                async {
                    mutex.withLock("sameKey") {
                        log.add("start2")
                        log.add("end2")
                    }
                }

            job1.await()
            job2.await()

            // job2 should not start until job1 ends
            val start2Index = log.indexOf("start2")
            val end1Index = log.indexOf("end1")
            assertTrue(start2Index > end1Index, "Expected start2 after end1, but got $log")
        }

    @Test
    fun `different keys can run concurrently`() =
        runTest {
            val mutex = StripedMutex(stripeCount = 1024) // large count to reduce collision
            val log = mutableListOf<String>()

            val job1 =
                async {
                    mutex.withLock("keyA") {
                        log.add("startA")
                        delay(50)
                        log.add("endA")
                    }
                }
            delay(10)
            val job2 =
                async {
                    mutex.withLock("keyB") {
                        log.add("startB")
                        delay(50)
                        log.add("endB")
                    }
                }

            job1.await()
            job2.await()

            // With different keys and enough stripes, B should start before A ends
            val startBIndex = log.indexOf("startB")
            val endAIndex = log.indexOf("endA")
            assertTrue(startBIndex < endAIndex, "Expected keyB to start before keyA ends, but got $log")
        }

    @Test
    fun `stripe count of 1 serializes all keys`() =
        runTest {
            val mutex = StripedMutex(stripeCount = 1)
            val log = mutableListOf<String>()

            val job1 =
                async {
                    mutex.withLock("anyKey1") {
                        log.add("start1")
                        delay(50)
                        log.add("end1")
                    }
                }
            delay(10)
            val job2 =
                async {
                    mutex.withLock("anyKey2") {
                        log.add("start2")
                        log.add("end2")
                    }
                }

            job1.await()
            job2.await()

            // With stripeCount=1, all keys share one mutex
            val start2Index = log.indexOf("start2")
            val end1Index = log.indexOf("end1")
            assertTrue(start2Index > end1Index, "Expected all keys serialized with stripeCount=1, got $log")
        }

    @Test
    fun `negative hashCode keys do not cause index out of bounds`() =
        runTest {
            val mutex = StripedMutex(stripeCount = 4)
            // Object with negative hashCode
            val key =
                object {
                    override fun hashCode(): Int = Int.MIN_VALUE
                }
            val result = mutex.withLock(key) { "ok" }
            assertEquals("ok", result)
        }
}
