package com.crosspaste.db.sync

import app.cash.turbine.test
import com.crosspaste.app.AppInfo
import com.crosspaste.db.TestDriverFactory
import com.crosspaste.db.createDatabase
import com.crosspaste.db.sync.SyncRuntimeInfo.Companion.createSyncRuntimeInfo
import com.crosspaste.dto.sync.EndpointInfo
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.platform.Platform
import kotlinx.coroutines.test.runTest
import kotlin.collections.listOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncRuntimeInfoDaoTest {

    private val testPlatform = Platform(
        name = "TestOS",
        arch = "x64",
        bitMode = 64,
        version = "1.0"
    )

    private val testSyncInfo = SyncInfo(
        appInfo = AppInfo(
            appInstanceId = "test-instance-1",
            appVersion = "1.0.0",
            appRevision = "12345",
            userName = "testUser",
        ),
         endpointInfo = EndpointInfo(
             deviceId = "device-123",
             deviceName = "TestDevice",
             platform = testPlatform,
             hostInfoList = listOf(HostInfo(32, "192.168.1.100")),
             port = 8080,
         )
    )

    private val testSyncRuntimeInfo = createSyncRuntimeInfo(testSyncInfo)

    private val updatedSyncRuntimeInfo = testSyncRuntimeInfo.copy(
        connectState = 1
    )

    private val database = createDatabase(TestDriverFactory())

    private val syncRuntimeInfoDao = SyncRuntimeInfoDao(database)

    @Test
    fun `getAllSyncRuntimeInfosFlow reacts to update function`() = runTest {
        // Pre-insert test data
        syncRuntimeInfoDao.insertOrUpdateSyncInfo(testSyncInfo)

        // Collect the Flow and verify the initial state
        syncRuntimeInfoDao.getAllSyncRuntimeInfosFlow().test {
            // Verify initial value
            val initialList = awaitItem()
            assertEquals(1, initialList.size)
            assertEquals(testSyncRuntimeInfo.appInstanceId, initialList[0].appInstanceId)
            assertEquals(testSyncRuntimeInfo.connectState, initialList[0].connectState)

            // Update the data using the update function
            syncRuntimeInfoDao.updateConnectInfo(updatedSyncRuntimeInfo)

            // Verify the Flow emits the updated value
            val updatedList = awaitItem()
            assertEquals(1, updatedList.size)
            assertEquals(updatedSyncRuntimeInfo.appInstanceId, updatedList[0].appInstanceId)
            assertEquals(updatedSyncRuntimeInfo.connectState, updatedList[0].connectState)

            // Verify no further emissions
            expectNoEvents()

            // Cancel the test
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateList triggers flow emission`() = runTest {
        // Pre-insert test data
        syncRuntimeInfoDao.insertOrUpdateSyncInfo(testSyncInfo)

        syncRuntimeInfoDao.getAllSyncRuntimeInfosFlow().test {
            // Verify initial value
            val initialList = awaitItem()
            assertEquals(1, initialList.size)

            // Update data using updateList
            syncRuntimeInfoDao.updateConnectInfo(updatedSyncRuntimeInfo)

            // Verify the Flow emits the updated value
            val updatedItems = awaitItem()
            assertEquals(1, updatedItems.size)
            assertEquals(updatedSyncRuntimeInfo.connectState, updatedItems[0].connectState)

            // Verify no further emissions
            expectNoEvents()

            // Cancel the test
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAllSyncRuntimeInfosFlow reacts to insertOrUpdateSyncInfo`() = runTest {
        // Collect the Flow and verify updates
        syncRuntimeInfoDao.getAllSyncRuntimeInfosFlow().test {
            // Initially should be an empty list
            val emptyList = awaitItem()
            assertTrue(emptyList.isEmpty())

            // Insert new data
            syncRuntimeInfoDao.insertOrUpdateSyncInfo(testSyncInfo)

            // Verify the Flow emits a list containing the new data
            val insertedList = awaitItem()
            assertEquals(1, insertedList.size)
            assertEquals(testSyncRuntimeInfo.appInstanceId, insertedList[0].appInstanceId)

            // Update the data
            syncRuntimeInfoDao.insertOrUpdateSyncInfo(testSyncInfo)

            // Verify the Flow emits a list containing the updated data
            val updatedList = awaitItem()
            assertEquals(1, updatedList.size)
            assertEquals(SyncState.DISCONNECTED, updatedList[0].connectState)

            // Verify no further emissions
            expectNoEvents()

            // Cancel the test
            cancelAndIgnoreRemainingEvents()
        }
    }
}