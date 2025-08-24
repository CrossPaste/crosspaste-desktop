package com.crosspaste.sync

import com.crosspaste.app.RatingPromptManager
import com.crosspaste.db.sync.ChangeType
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.db.sync.SyncState
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.net.NetworkInterfaceService
import com.crosspaste.net.SyncInfoFactory
import com.crosspaste.net.TelnetHelper
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.platform.Platform
import com.crosspaste.secure.SecureStore
import com.crosspaste.ui.base.DialogService
import com.crosspaste.ui.base.PasteDialogFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
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
            dialogService = mockk(relaxed = true),
            networkInterfaceService = mockk(relaxed = true),
            pasteDialogFactory = mockk(relaxed = true),
            ratingPromptManager = mockk(relaxed = true),
            secureStore = mockk(relaxed = true),
            syncClientApi = mockk(relaxed = true),
            syncInfoFactory = mockk(relaxed = true),
            syncRuntimeInfoDao = mockk(relaxed = true),
            telnetHelper = mockk(relaxed = true),
            tokenCache = TokenCache,
            nearbyDeviceManager = mockk(relaxed = true),
        )

    private fun createSyncManager(
        mocks: Mocks,
        scope: CoroutineScope,
    ): GeneralSyncManager =
        GeneralSyncManager(
            dialogService = mocks.dialogService,
            networkInterfaceService = mocks.networkInterfaceService,
            pasteDialogFactory = mocks.pasteDialogFactory,
            ratingPromptManager = mocks.ratingPromptManager,
            realTimeSyncScope = scope,
            secureStore = mocks.secureStore,
            syncClientApi = mocks.syncClientApi,
            syncInfoFactory = mocks.syncInfoFactory,
            syncRuntimeInfoDao = mocks.syncRuntimeInfoDao,
            telnetHelper = mocks.telnetHelper,
            tokenCache = mocks.tokenCache,
            lazyNearbyDeviceManager = lazy { mocks.nearbyDeviceManager },
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
        val dialogService: DialogService,
        val networkInterfaceService: NetworkInterfaceService,
        val pasteDialogFactory: PasteDialogFactory,
        val ratingPromptManager: RatingPromptManager,
        val secureStore: SecureStore,
        val syncClientApi: SyncClientApi,
        val syncInfoFactory: SyncInfoFactory,
        val syncRuntimeInfoDao: SyncRuntimeInfoDao,
        val telnetHelper: TelnetHelper,
        val tokenCache: TokenCache,
        val nearbyDeviceManager: NearbyDeviceManager,
    )

    @Test
    fun testInitialState() =
        runTest {
            val mocks = createMocks()
            val syncManager = createSyncManager(mocks, this)

            assertTrue(syncManager.realTimeSyncRuntimeInfos.value.isEmpty())
            assertTrue(syncManager.getSyncHandlers().isEmpty())
        }

    @Test
    fun testStartOnlyCallsOnce() =
        runTest {
            val mocks = createMocks()
            val testSyncRuntimeInfo = createTestSyncRuntimeInfo()
            coEvery { mocks.syncRuntimeInfoDao.getAllSyncRuntimeInfos() } returns listOf(testSyncRuntimeInfo)
            every { mocks.syncRuntimeInfoDao.getAllSyncRuntimeInfosFlow() } returns
                MutableStateFlow(listOf(testSyncRuntimeInfo))
            every { mocks.nearbyDeviceManager.updateSyncManager() } just runs
            coEvery { mocks.telnetHelper.switchHost(any(), any()) } returns null

            val childScope = CoroutineScope(coroutineContext + Job())
            val syncManager = createSyncManager(mocks, childScope)

            syncManager.start()
            syncManager.start() // Second call should be ignored

            advanceUntilIdle()

            coVerify(exactly = 1) { mocks.syncRuntimeInfoDao.getAllSyncRuntimeInfos() }

            assertEquals(1, syncManager.getSyncHandlers().size)

            syncManager.stop()
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
            assertEquals(testSyncInfo.appInstanceId, handler.getCurrentSyncRuntimeInfo().appInstanceId)
        }

    @Test
    fun testRemoveSyncHandler() =
        runTest {
            val mocks = createMocks()
            val testSyncInfo = createTestSyncRuntimeInfo()

            coEvery { mocks.syncRuntimeInfoDao.deleteSyncRuntimeInfo(any()) } just runs

            val syncManager = createSyncManager(mocks, this)

            syncManager.removeSyncHandler(testSyncInfo.appInstanceId)
            advanceTimeBy(100)

            coVerify { mocks.syncRuntimeInfoDao.deleteSyncRuntimeInfo(testSyncInfo.appInstanceId) }
        }

    @Test
    fun testTrustByTokenWithNonExistentHandler() =
        runTest {
            val mocks = createMocks()
            val syncManager = createSyncManager(mocks, this)

            // Should not throw exception when handler doesn't exist
            syncManager.trustByToken("non-existent", 123456)
            advanceTimeBy(100)
        }

    @Test
    fun testUpdateSyncInfoNewInstance() =
        runTest {
            val mocks = createMocks()
            val syncInfo =
                mockk<SyncInfo> {
                    every { appInfo.appInstanceId } returns "test-app-1"
                }
            val testSyncRuntimeInfo = createTestSyncRuntimeInfo()

            coEvery {
                mocks.syncRuntimeInfoDao.insertOrUpdateSyncInfo(syncInfo)
            } returns (ChangeType.NEW_INSTANCE to testSyncRuntimeInfo)

            val syncManager = createSyncManager(mocks, this)
            syncManager.updateSyncInfo(syncInfo, refresh = false)
            advanceTimeBy(100)

            coVerify { mocks.syncRuntimeInfoDao.insertOrUpdateSyncInfo(syncInfo) }
        }

    @Test
    fun testRefreshDoesNotThrowException() =
        runTest {
            val mocks = createMocks()
            val syncManager = createSyncManager(mocks, this)

            // Test that refresh methods don't throw exceptions
            syncManager.refresh(callback = { })
            syncManager.refresh(listOf("non-existent-1", "non-existent-2"), callback = { })
            advanceTimeBy(100) // Just ensure no immediate crashes
        }

    @Test
    fun testIgnoreVerifyAndToVerify() =
        runTest {
            val mocks = createMocks()
            val syncManager = createSyncManager(mocks, this)

            // These methods should not throw exceptions
            syncManager.ignoreVerify("test-app-1")
            advanceTimeBy(100)

            syncManager.toVerify("test-app-1")
            advanceTimeBy(100)
        }

    @Test
    fun testStop() =
        runTest {
            val mocks = createMocks()
            coEvery { mocks.syncRuntimeInfoDao.getAllSyncRuntimeInfos() } returns emptyList()
            every { mocks.syncRuntimeInfoDao.getAllSyncRuntimeInfosFlow() } returns MutableStateFlow(emptyList())
            every { mocks.nearbyDeviceManager.updateSyncManager() } just runs

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

            coEvery { mocks.syncRuntimeInfoDao.getAllSyncRuntimeInfos() } returns emptyList()
            every { mocks.syncRuntimeInfoDao.getAllSyncRuntimeInfosFlow() } returns syncInfosFlow
            every { mocks.nearbyDeviceManager.updateSyncManager() } just runs
            every { mocks.nearbyDeviceManager.refreshSyncManager() } just runs
            coEvery { mocks.telnetHelper.switchHost(any(), any()) } returns null

            val childScope = CoroutineScope(coroutineContext + Job())
            val syncManager = createSyncManager(mocks, childScope)
            syncManager.start()

            advanceTimeBy(1000)

            assertEquals(0, syncManager.realTimeSyncRuntimeInfos.value.size)
            assertEquals(0, syncManager.getSyncHandlers().size)

            verify { mocks.nearbyDeviceManager.updateSyncManager() }

            syncManager.stop()
        }

    @Test
    fun testCreateSyncHandlerWithMultipleRuntimeInfos() =
        runTest {
            val mocks = createMocks()

            coEvery { mocks.telnetHelper.switchHost(any(), any()) } returns null

            val syncManager = createSyncManager(mocks, this)

            val testSyncInfo1 = createTestSyncRuntimeInfo("app-1", "device-1")
            val testSyncInfo2 = createTestSyncRuntimeInfo("app-2", "device-2")

            // Test createSyncHandler functionality directly
            val handler1 = syncManager.createSyncHandler(testSyncInfo1)
            val handler2 = syncManager.createSyncHandler(testSyncInfo2)

            // Verify handlers are created correctly
            assertNotNull(handler1)
            assertNotNull(handler2)
            assertEquals("app-1", handler1.getCurrentSyncRuntimeInfo().appInstanceId)
            assertEquals("app-2", handler2.getCurrentSyncRuntimeInfo().appInstanceId)
            assertEquals("device-1", handler1.getCurrentSyncRuntimeInfo().deviceId)
            assertEquals("device-2", handler2.getCurrentSyncRuntimeInfo().deviceId)
        }
}
