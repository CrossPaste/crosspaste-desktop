package com.crosspaste.app

import com.crosspaste.path.TestAppPathProviderMock.useMockAppPathProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLockTest {

    @Test
    fun testSingleInstanceLock() {
        useMockAppPathProvider { _, _, _, _ ->
            runBlocking {
                val job1 =
                    launch {
                        val appLockState = DesktopAppLaunch.acquireLock()
                        assertTrue(appLockState.acquiredLock, "First instance should be able to acquire the lock")
                        assertTrue(appLockState.firstLaunch, "First instance should be considered as the first launch")
                    }

                delay(100)

                val job2 =
                    launch {
                        val appLockState = DesktopAppLaunch.acquireLock()
                        assertFalse(
                            appLockState.acquiredLock,
                            "Second instance should not be able to acquire the lock while the first one holds it",
                        )
                        assertFalse(appLockState.firstLaunch, "Second instance should not be considered as the first launch")
                    }

                job1.join()
                job2.join()

                DesktopAppLaunch.releaseLock()
            }
        }
    }

    @Test
    fun testLockRelease() {
        useMockAppPathProvider { _, _, _, _ ->
            runBlocking {
                assertTrue(DesktopAppLaunch.acquireLock().acquiredLock, "Instance should be able to acquire the lock initially")
                DesktopAppLaunch.releaseLock()

                val job =
                    launch {
                        val appLockState = DesktopAppLaunch.acquireLock()
                        assertTrue(
                            appLockState.acquiredLock,
                            "Instance should be able to reacquire the lock after it was released",
                        )
                        assertFalse(appLockState.firstLaunch, "Instance should not be considered as the first launch")
                    }

                job.join()
                DesktopAppLaunch.releaseLock()
            }
        }
    }
}
