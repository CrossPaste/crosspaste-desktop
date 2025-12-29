package com.crosspaste.sync

import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.db.sync.SyncState
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.platform.Platform
import com.crosspaste.ui.devices.DeviceScopeFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GeneralSyncManagerTest {

    private fun createMocks(): Mocks =
        Mocks(
            deviceScopeFactory = mockk(relaxed = true),
            syncResolver = mockk(relaxed = true),
            syncRuntimeInfoDao = mockk(relaxed = true),
        )

    private fun createSyncManager(
        mocks: Mocks,
        scope: CoroutineScope,
    ): GeneralSyncManager =
        GeneralSyncManager(
            realTimeSyncScope = scope,
            syncResolver = mocks.syncResolver,
            syncRuntimeInfoDao = mocks.syncRuntimeInfoDao,
        )

    private fun createTestSyncRuntimeInfo(
        appInstanceId: String = "test-app-1",
        deviceId: String = "test-device-1",
        connectState: Int = SyncState.CONNECTED,
    ): SyncRuntimeInfo =
        SyncRuntimeInfo(
            appInstanceId = appInstanceId,
            appVersion = "1.0.0",
            userName = "testUser",
            deviceId = deviceId,
            deviceName = "Test Device",
            platform =
                Platform(
                    name = "Desktop",
                    arch = "x86_64",
                    bitMode = 64,
                    version = "1.0.0",
                ),
            connectState = connectState,
            allowSend = true,
            allowReceive = true,
        )

    data class Mocks(
        val deviceScopeFactory: DeviceScopeFactory,
        val syncResolver: SyncResolver,
        val syncRuntimeInfoDao: SyncRuntimeInfoDao,
    )

    @Test
    fun testInitialState() =
        runTest {
            val mocks = createMocks()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncManager = createSyncManager(mocks, childScope)

            assertTrue(syncManager.realTimeSyncRuntimeInfos.value.isEmpty())
            assertTrue(syncManager.getSyncHandlers().isEmpty())
        }

    @Test
    fun testStartOnlyCallsOnce() =
        runTest {
            val mocks = createMocks()
            val testSyncRuntimeInfo = createTestSyncRuntimeInfo()
            every { mocks.syncRuntimeInfoDao.getAllSyncRuntimeInfosFlow() } returns
                MutableStateFlow(listOf(testSyncRuntimeInfo))

            coEvery { mocks.syncResolver.emitEvent(any()) } just runs

            val childScope = CoroutineScope(coroutineContext + Job())
            val syncManager = createSyncManager(mocks, childScope)

            syncManager.start()
            syncManager.start() // Second call should be ignored

            advanceUntilIdle()

            assertEquals(1, syncManager.getSyncHandlers().size)
        }

    @Test
    fun testCreateSyncHandler() =
        runTest {
            val mocks = createMocks()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncManager = createSyncManager(mocks, childScope)
            val testSyncInfo = createTestSyncRuntimeInfo()

            val handler = syncManager.createSyncHandler(testSyncInfo)

            assertNotNull(handler)
            assertEquals(testSyncInfo.appInstanceId, handler.currentSyncRuntimeInfo.appInstanceId)
        }

    @Test
    fun testRemoveSyncHandler() =
        runTest {
            val mocks = createMocks()
            val testSyncRuntimeInfo = createTestSyncRuntimeInfo()

            every { mocks.syncRuntimeInfoDao.getAllSyncRuntimeInfosFlow() } returns
                MutableStateFlow(listOf(testSyncRuntimeInfo))

            coEvery { mocks.syncResolver.emitEvent(any()) } just runs

            val childScope = CoroutineScope(coroutineContext + Job())
            val syncManager = createSyncManager(mocks, childScope)

            syncManager.start()

            advanceUntilIdle()

            syncManager.removeSyncHandler(testSyncRuntimeInfo.appInstanceId)

            advanceUntilIdle()

            coVerify { mocks.syncResolver.emitEvent(any()) }
        }

    @Test
    fun testTrustByTokenWithNonExistentHandler() =
        runTest {
            val mocks = createMocks()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncManager = createSyncManager(mocks, childScope)

            // Should not throw exception when handler doesn't exist
            syncManager.trustByToken("non-existent", 123456)
        }

    @Test
    fun testUpdateSyncInfoNewInstance() =
        runTest {
            val mocks = createMocks()
            val syncInfo =
                mockk<SyncInfo> {
                    every { appInfo.appInstanceId } returns "test-app-1"
                }

            coEvery { mocks.syncResolver.emitEvent(any()) } just runs

            val childScope = CoroutineScope(coroutineContext + Job())
            val syncManager = createSyncManager(mocks, childScope)
            syncManager.start()

            syncManager.updateSyncInfo(syncInfo)
            advanceUntilIdle()

            coVerify { mocks.syncResolver.emitEvent(any()) }
        }

    @Test
    fun testRefreshDoesNotThrowException() =
        runTest {
            val mocks = createMocks()
            val testSyncRuntimeInfo = createTestSyncRuntimeInfo()

            every { mocks.syncRuntimeInfoDao.getAllSyncRuntimeInfosFlow() } returns
                MutableStateFlow(listOf(testSyncRuntimeInfo))

            coEvery { mocks.syncResolver.emitEvent(any()) } just runs

            val childScope = CoroutineScope(coroutineContext + Job())
            val syncManager = createSyncManager(mocks, childScope)

            syncManager.start()

            // Test that refresh methods don't throw exceptions
            syncManager.refresh(callback = { })
            advanceUntilIdle()

            coVerify { mocks.syncResolver.emitEvent(any()) }

            syncManager.refresh(listOf("test-app-1"), callback = { })
            advanceUntilIdle()

            coVerify { mocks.syncResolver.emitEvent(any()) }
        }

    @Test
    fun testStop() =
        runTest {
            val mocks = createMocks()
            every { mocks.syncRuntimeInfoDao.getAllSyncRuntimeInfosFlow() } returns
                MutableStateFlow(emptyList())

            val childScope = CoroutineScope(coroutineContext + Job())
            val syncManager = createSyncManager(mocks, childScope)

            syncManager.start()
            advanceTimeBy(100)
            syncManager.stop()

            // Should be able to call stop multiple times
            syncManager.stop()
        }

    @Test
    fun testStartInitializesFromDatabaseEmptyList() =
        runTest {
            val mocks = createMocks()
            val syncInfosFlow = MutableStateFlow<List<SyncRuntimeInfo>>(emptyList())

            every { mocks.syncRuntimeInfoDao.getAllSyncRuntimeInfosFlow() } returns syncInfosFlow

            val childScope = CoroutineScope(coroutineContext + Job())
            val syncManager = createSyncManager(mocks, childScope)
            syncManager.start()

            advanceUntilIdle()

            assertEquals(0, syncManager.realTimeSyncRuntimeInfos.value.size)
            assertEquals(0, syncManager.getSyncHandlers().size)

            syncManager.stop()
        }

    @Test
    fun testCreateSyncHandlerWithMultipleRuntimeInfos() =
        runTest {
            val mocks = createMocks()

            val testSyncInfo1 = createTestSyncRuntimeInfo("app-1", "device-1")
            val testSyncInfo2 = createTestSyncRuntimeInfo("app-2", "device-2")

            val syncInfosFlow = MutableStateFlow<List<SyncRuntimeInfo>>(emptyList())

            every { mocks.syncRuntimeInfoDao.getAllSyncRuntimeInfosFlow() } returns syncInfosFlow

            val childScope = CoroutineScope(coroutineContext + Job())
            val syncManager = createSyncManager(mocks, childScope)
            syncManager.start()

            syncInfosFlow.value = listOf(testSyncInfo1, testSyncInfo2)

            advanceUntilIdle()

            // Test createSyncHandler functionality directly
            val handler1 = syncManager.getSyncHandler(testSyncInfo1.appInstanceId)
            val handler2 = syncManager.getSyncHandler(testSyncInfo2.appInstanceId)

            // Verify handlers are created correctly
            assertNotNull(handler1)
            assertNotNull(handler2)
            assertEquals("app-1", handler1.currentSyncRuntimeInfo.appInstanceId)
            assertEquals("app-2", handler2.currentSyncRuntimeInfo.appInstanceId)
            assertEquals("device-1", handler1.currentSyncRuntimeInfo.deviceId)
            assertEquals("device-2", handler2.currentSyncRuntimeInfo.deviceId)
        }
}
