package com.crosspaste.app

import com.crosspaste.path.AppPathProvider
import com.crosspaste.path.FakeAppPathProvider
import com.crosspaste.platform.DesktopPlatformProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLockTest : KoinTest {

    companion object {

        private val platform = DesktopPlatformProvider().getPlatform()

        private val testModule =
            module {
                single<AppLock> { DesktopAppLaunch(platform, get()) }
                factory<AppPathProvider> { FakeAppPathProvider() }
            }
    }

    @BeforeTest
    fun setUp() {
        GlobalContext.startKoin {
            modules(testModule)
        }
    }

    @AfterTest
    fun tearDown() {
        GlobalContext.stopKoin()
    }

    @Test
    fun testSingleInstanceLock() {
        runTest {
            val appLock by inject<AppLock>()
            val job1 =
                launch {
                    val appLockState = appLock.acquireLock()
                    assertTrue(appLockState.acquiredLock, "First instance should be able to acquire the lock")
                    assertTrue(appLockState.firstLaunch, "First instance should be considered as the first launch")
                }

            delay(100)

            val job2 =
                launch {
                    val appLockState = appLock.acquireLock()
                    assertFalse(
                        appLockState.acquiredLock,
                        "Second instance should not be able to acquire the lock while the first one holds it",
                    )
                    assertFalse(
                        appLockState.firstLaunch,
                        "Second instance should not be considered as the first launch",
                    )
                }

            job1.join()
            job2.join()

            appLock.releaseLock()
        }
    }

    @Test
    fun testLockRelease() {
        runTest {
            val appLock by inject<AppLock>()
            assertTrue(appLock.acquireLock().acquiredLock, "Instance should be able to acquire the lock initially")
            appLock.releaseLock()

            val job =
                launch {
                    val appLockState = appLock.acquireLock()
                    assertTrue(
                        appLockState.acquiredLock,
                        "Instance should be able to reacquire the lock after it was released",
                    )
                    assertFalse(appLockState.firstLaunch, "Instance should not be considered as the first launch")
                }

            job.join()
            appLock.releaseLock()
        }
    }
}
