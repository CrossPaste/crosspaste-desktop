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
                        assertTrue(DesktopAppLock.acquireLock(), "First instance should be able to acquire the lock")
                    }

                delay(100)

                val job2 =
                    launch {
                        assertFalse(
                            DesktopAppLock.acquireLock(),
                            "Second instance should not be able to acquire the lock while the first one holds it",
                        )
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
                assertTrue(DesktopAppLock.acquireLock(), "Instance should be able to acquire the lock initially")
                DesktopAppLock.releaseLock()

                val job =
                    launch {
                        assertTrue(
                            DesktopAppLock.acquireLock(),
                            "Instance should be able to reacquire the lock after it was released",
                        )
                    }

                job.join()
                DesktopAppLock.releaseLock()
            }
        }
    }
}
