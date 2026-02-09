package com.crosspaste.sync

import com.crosspaste.app.RatingPromptManager
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.sync.HostInfo
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.sync.SyncTestFixtures.createSyncInfo
import com.crosspaste.sync.SyncTestFixtures.createSyncRuntimeInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GeneralNearbyDeviceManagerTest {

    private class TestDeps(
        scope: CoroutineScope,
    ) {
        val appInfo = SyncTestFixtures.createAppInfo(appInstanceId = "self-app-id")
        val configManager: CommonConfigManager = mockk(relaxed = true)
        val ratingPromptManager: RatingPromptManager = mockk(relaxed = true)
        val syncManager: SyncManager = mockk(relaxed = true)
        val realTimeSyncRuntimeInfos = MutableStateFlow<List<SyncRuntimeInfo>>(emptyList())

        init {
            val configFlow = MutableStateFlow(createMockConfig("[]"))
            every { configManager.config } returns configFlow
            every { configManager.getCurrentConfig() } returns configFlow.value
            every { syncManager.realTimeSyncRuntimeInfos } returns realTimeSyncRuntimeInfos
        }

        fun createManager(scope: CoroutineScope): GeneralNearbyDeviceManager =
            GeneralNearbyDeviceManager(
                appInfo = appInfo,
                configManager = configManager,
                ratingPromptManager = ratingPromptManager,
                syncManager = syncManager,
                nearbyDeviceScope = scope,
            )
    }

    @Test
    fun addDevice_newDevice_appearsInNearbySyncInfos() =
        runTest {
            val childScope = CoroutineScope(coroutineContext + Job())
            val deps = TestDeps(childScope)
            val manager = deps.createManager(childScope)

            val syncInfo = createSyncInfo(appInstanceId = "other-app-1")
            manager.addDevice(syncInfo)
            advanceUntilIdle()

            val infos = manager.nearbySyncInfos.value
            assertTrue(infos.any { it.appInfo.appInstanceId == "other-app-1" })
        }

    @Test
    fun addDevice_selfFiltered_doesNotAppear() =
        runTest {
            val childScope = CoroutineScope(coroutineContext + Job())
            val deps = TestDeps(childScope)
            val manager = deps.createManager(childScope)

            val selfSyncInfo = createSyncInfo(appInstanceId = "self-app-id")
            manager.addDevice(selfSyncInfo)
            advanceUntilIdle()

            assertTrue(manager.nearbySyncInfos.value.isEmpty())
        }

    @Test
    fun addDevice_alreadySynced_filteredFromNearbySyncInfos() =
        runTest {
            val childScope = CoroutineScope(coroutineContext + Job())
            val deps = TestDeps(childScope)
            val manager = deps.createManager(childScope)

            // Mark the device as already synced
            deps.realTimeSyncRuntimeInfos.value =
                listOf(createSyncRuntimeInfo(appInstanceId = "other-app-1"))
            advanceUntilIdle()

            val syncInfo = createSyncInfo(appInstanceId = "other-app-1")
            manager.addDevice(syncInfo)
            advanceUntilIdle()

            // Already synced devices should not appear in nearby list
            val nearbyIds = manager.nearbySyncInfos.value.map { it.appInfo.appInstanceId }
            assertFalse(nearbyIds.contains("other-app-1"))
        }

    @Test
    fun removeDevice_existingDevice_removed() =
        runTest {
            val childScope = CoroutineScope(coroutineContext + Job())
            val deps = TestDeps(childScope)
            val manager = deps.createManager(childScope)

            val syncInfo = createSyncInfo(appInstanceId = "other-app-1")
            manager.addDevice(syncInfo)
            advanceUntilIdle()
            assertTrue(manager.nearbySyncInfos.value.isNotEmpty())

            manager.removeDevice(syncInfo)
            advanceUntilIdle()

            assertTrue(manager.nearbySyncInfos.value.isEmpty())
            verify(exactly = 2) { deps.ratingPromptManager.trackSignificantAction() }
        }

    @Test
    fun removeDevice_nonExistentDevice_noEffect() =
        runTest {
            val childScope = CoroutineScope(coroutineContext + Job())
            val deps = TestDeps(childScope)
            val manager = deps.createManager(childScope)

            val syncInfo = createSyncInfo(appInstanceId = "non-existent")
            manager.removeDevice(syncInfo)
            advanceUntilIdle()

            // Should not throw and should not call trackSignificantAction
            verify(exactly = 0) { deps.ratingPromptManager.trackSignificantAction() }
        }

    @Test
    fun addDevice_twice_mergesHostInfo() =
        runTest {
            val childScope = CoroutineScope(coroutineContext + Job())
            val deps = TestDeps(childScope)
            val manager = deps.createManager(childScope)

            val syncInfo1 =
                createSyncInfo(
                    appInstanceId = "other-app-1",
                    hostInfoList = listOf(HostInfo(24, "192.168.1.100")),
                )
            val syncInfo2 =
                createSyncInfo(
                    appInstanceId = "other-app-1",
                    hostInfoList = listOf(HostInfo(24, "10.0.0.1")),
                )

            manager.addDevice(syncInfo1)
            manager.addDevice(syncInfo2)
            advanceUntilIdle()

            val infos = manager.nearbySyncInfos.value
            assertEquals(1, infos.size)
            // Should have merged host info
            assertTrue(infos[0].endpointInfo.hostInfoList.size >= 1)
        }

    @Test
    fun startSearching_setsSearchingTrue() =
        runTest {
            val childScope = CoroutineScope(coroutineContext + Job())
            val deps = TestDeps(childScope)
            val manager = deps.createManager(childScope)

            assertFalse(manager.searching.value)
            manager.startSearching()
            assertTrue(manager.searching.value)
        }

    @Test
    fun stopSearching_setsSearchingFalse() =
        runTest {
            val childScope = CoroutineScope(coroutineContext + Job())
            val deps = TestDeps(childScope)
            val manager = deps.createManager(childScope)

            manager.startSearching()
            assertTrue(manager.searching.value)

            manager.stopSearching()
            assertFalse(manager.searching.value)
        }

    @Test
    fun addDevice_tracksSignificantAction() =
        runTest {
            val childScope = CoroutineScope(coroutineContext + Job())
            val deps = TestDeps(childScope)
            val manager = deps.createManager(childScope)

            val syncInfo = createSyncInfo(appInstanceId = "other-app-1")
            manager.addDevice(syncInfo)

            verify(exactly = 1) { deps.ratingPromptManager.trackSignificantAction() }
        }

    @Test
    fun addDevice_existingSyncPortChange_triggersUpdateSyncInfo() =
        runTest {
            val childScope = CoroutineScope(coroutineContext + Job())
            val deps = TestDeps(childScope)
            val manager = deps.createManager(childScope)

            // Set up existing synced device
            deps.realTimeSyncRuntimeInfos.value =
                listOf(createSyncRuntimeInfo(appInstanceId = "other-app-1", port = 13129))
            advanceUntilIdle()

            // Add device with different port
            val syncInfo = createSyncInfo(appInstanceId = "other-app-1", port = 13130)
            manager.addDevice(syncInfo)
            advanceUntilIdle()

            // diffSyncInfo should detect port change and trigger updateSyncInfo
            verify { deps.syncManager.updateSyncInfo(any()) }
        }

    companion object {
        private fun createMockConfig(blacklist: String): com.crosspaste.config.AppConfig {
            val config = mockk<com.crosspaste.config.AppConfig>(relaxed = true)
            every { config.blacklist } returns blacklist
            return config
        }
    }
}
