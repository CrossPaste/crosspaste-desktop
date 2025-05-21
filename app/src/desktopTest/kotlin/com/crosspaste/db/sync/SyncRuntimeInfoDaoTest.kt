package com.crosspaste.db.sync

import app.cash.turbine.test
import com.crosspaste.db.TestDriverFactory
import com.crosspaste.db.createDatabase
import com.crosspaste.platform.Platform
import kotlinx.coroutines.test.runTest
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

    private val testSyncRuntimeInfo = SyncRuntimeInfo(
        appInstanceId = "test-instance-1",
        appVersion = "1.0.0",
        userName = "testUser",
        deviceId = "device-123",
        deviceName = "TestDevice",
        platform = testPlatform,
        hostInfoList = listOf(HostInfo(32, "192.168.1.100")),
        port = 8080,
        noteName = "Test Note",
        connectNetworkPrefixLength = 24,
        connectHostAddress = "192.168.1.100",
        connectState = 0,
        allowSend = true,
        allowReceive = true
    )

    private val updatedSyncRuntimeInfo = testSyncRuntimeInfo.copy(
        noteName = "Updated Note",
        connectState = 1
    )

    private val database = createDatabase(TestDriverFactory())

    private val syncRuntimeInfoDao = SyncRuntimeInfoDao(database)

    @Test
    fun `getAllSyncRuntimeInfosFlow reacts to update function`() = runTest {
        // Pre-insert test data
        syncRuntimeInfoDao.insertOrUpdateSyncRuntimeInfo(testSyncRuntimeInfo)

        // Collect the Flow and verify the initial state
        syncRuntimeInfoDao.getAllSyncRuntimeInfosFlow().test {
            // Verify initial value
            val initialList = awaitItem()
            assertEquals(1, initialList.size)
            assertEquals(testSyncRuntimeInfo.appInstanceId, initialList[0].appInstanceId)
            assertEquals(testSyncRuntimeInfo.noteName, initialList[0].noteName)

            // Update the data using the update function
            syncRuntimeInfoDao.update(updatedSyncRuntimeInfo)

            // Verify the Flow emits the updated value
            val updatedList = awaitItem()
            assertEquals(1, updatedList.size)
            assertEquals(updatedSyncRuntimeInfo.appInstanceId, updatedList[0].appInstanceId)
            assertEquals(updatedSyncRuntimeInfo.noteName, updatedList[0].noteName)
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
        syncRuntimeInfoDao.insertOrUpdateSyncRuntimeInfo(testSyncRuntimeInfo)

        // Create update list
        val updatedList = listOf(updatedSyncRuntimeInfo)

        syncRuntimeInfoDao.getAllSyncRuntimeInfosFlow().test {
            // Verify initial value
            val initialList = awaitItem()
            assertEquals(1, initialList.size)

            // Update data using updateList
            syncRuntimeInfoDao.updateList(updatedList)

            // Verify the Flow emits the updated value
            val updatedItems = awaitItem()
            assertEquals(1, updatedItems.size)
            assertEquals(updatedSyncRuntimeInfo.noteName, updatedItems[0].noteName)

            // Verify no further emissions
            expectNoEvents()

            // Cancel the test
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAllSyncRuntimeInfosFlow reacts to insertOrUpdateSyncRuntimeInfo`() = runTest {
        // Collect the Flow and verify updates
        syncRuntimeInfoDao.getAllSyncRuntimeInfosFlow().test {
            // Initially should be an empty list
            val emptyList = awaitItem()
            assertTrue(emptyList.isEmpty())

            // Insert new data
            syncRuntimeInfoDao.insertOrUpdateSyncRuntimeInfo(testSyncRuntimeInfo)

            // Verify the Flow emits a list containing the new data
            val insertedList = awaitItem()
            assertEquals(1, insertedList.size)
            assertEquals(testSyncRuntimeInfo.appInstanceId, insertedList[0].appInstanceId)

            // Update the data
            syncRuntimeInfoDao.insertOrUpdateSyncRuntimeInfo(updatedSyncRuntimeInfo)

            // Verify the Flow emits a list containing the updated data
            val updatedList = awaitItem()
            assertEquals(1, updatedList.size)
            assertEquals(updatedSyncRuntimeInfo.noteName, updatedList[0].noteName)

            // Verify no further emissions
            expectNoEvents()

            // Cancel the test
            cancelAndIgnoreRemainingEvents()
    }
}