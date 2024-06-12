package com.clipevery.app

import com.clipevery.path.TestPathProviderMock.Companion.testUseMockTestPathProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLockTest {

    @Test
    fun testSingleInstanceLock() {
        testUseMockTestPathProvider { _, _, _, _ ->
            runBlocking {
                val job1 =
                    launch {
                        val pair = DesktopAppLock.acquireLock()
                        assertTrue(pair.first, "First instance should be able to acquire the lock")
                        assertTrue(pair.second, "First instance should be considered as the first launch")
                    }

                delay(100)

                val job2 =
                    launch {
                        val pair = DesktopAppLock.acquireLock()
                        assertFalse(
                            pair.first,
                            "Second instance should not be able to acquire the lock while the first one holds it",
                        )
                        assertFalse(pair.second, "Second instance should not be considered as the first launch")
                    }

                job1.join()
                job2.join()

                DesktopAppLock.releaseLock()
            }
        }
    }

    @Test
    fun testLockRelease() {
        testUseMockTestPathProvider { _, _, _, _ ->
            runBlocking {
                assertTrue(DesktopAppLock.acquireLock().first, "Instance should be able to acquire the lock initially")
                DesktopAppLock.releaseLock()

                val job =
                    launch {
                        val pair = DesktopAppLock.acquireLock()
                        assertTrue(
                            pair.first,
                            "Instance should be able to reacquire the lock after it was released",
                        )
                        assertFalse(pair.second, "Instance should not be considered as the first launch")
                    }

                job.join()
                DesktopAppLock.releaseLock()
            }
        }
    }
}
