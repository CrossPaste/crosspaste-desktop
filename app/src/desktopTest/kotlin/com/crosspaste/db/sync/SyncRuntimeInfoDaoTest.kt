package com.crosspaste.db.sync

import app.cash.turbine.test
import com.crosspaste.app.AppInfo
import com.crosspaste.db.TestDriverFactory
import com.crosspaste.db.createDatabase
import com.crosspaste.db.sync.SyncRuntimeInfo.Companion.createSyncRuntimeInfo
import com.crosspaste.dto.sync.EndpointInfo
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.platform.Platform
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.collections.listOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

    private val syncRuntimeInfoDao = SqlSyncRuntimeInfoDao(database)

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

    // Regression: previously updateNotifier was a Channel<String>(UNLIMITED), and
    // multiple collectors would compete (fan-out) for each emission — adding a
    // second subscriber (e.g. MouseDaemonManager) caused half of the delete
    // events to be stolen, leaving the UI/sync state stale. The DAO now uses a
    // MutableSharedFlow broadcast signal so every subscriber sees every change.
    @Test
    fun `getAllSyncRuntimeInfosFlow broadcasts to multiple subscribers`() = runTest {
        syncRuntimeInfoDao.insertOrUpdateSyncInfo(testSyncInfo)

        // Two independent collectors must each receive the delete emission.
        val sub1 = async {
            syncRuntimeInfoDao.getAllSyncRuntimeInfosFlow().test {
                assertEquals(1, awaitItem().size)
                val afterDelete = awaitItem()
                assertTrue(afterDelete.isEmpty(), "subscriber 1 missed the delete")
                cancelAndIgnoreRemainingEvents()
            }
        }
        val sub2 = async {
            syncRuntimeInfoDao.getAllSyncRuntimeInfosFlow().test {
                assertEquals(1, awaitItem().size)
                val afterDelete = awaitItem()
                assertTrue(afterDelete.isEmpty(), "subscriber 2 missed the delete")
                cancelAndIgnoreRemainingEvents()
            }
        }

        // Defer the delete one tick so both `test {}` blocks have collected
        // their initial snapshot before the change signal fires.
        val deleter = async {
            syncRuntimeInfoDao.deleteSyncRuntimeInfo(testSyncRuntimeInfo.appInstanceId)
        }

        awaitAll(sub1, sub2, deleter)
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

            // Re-insert identical data — should NOT trigger emission (no actual change)
            syncRuntimeInfoDao.insertOrUpdateSyncInfo(testSyncInfo)
            expectNoEvents()

            // Update with changed data — should trigger emission
            val changedSyncInfo = SyncInfo(
                appInfo = AppInfo(
                    appInstanceId = "test-instance-1",
                    appVersion = "2.0.0",
                    appRevision = "12345",
                    userName = "testUser",
                ),
                endpointInfo = testSyncInfo.endpointInfo,
            )
            syncRuntimeInfoDao.insertOrUpdateSyncInfo(changedSyncInfo)

            val updatedList = awaitItem()
            assertEquals(1, updatedList.size)
            assertEquals("2.0.0", updatedList[0].appVersion)

            // Verify no further emissions
            expectNoEvents()

            // Cancel the test
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insertOrUpdateSyncInfo caps stored hostInfoList at MAX_RECENT_HOST_INFO`() = runTest {
        val manyHosts = (1..(HostInfo.MAX_RECENT_HOST_INFO + 4)).map { HostInfo(24, "10.0.0.$it") }
        syncRuntimeInfoDao.insertOrUpdateSyncInfo(
            testSyncInfo.copy(
                endpointInfo = testSyncInfo.endpointInfo.copy(hostInfoList = manyHosts),
            ),
        )

        val stored = syncRuntimeInfoDao.getSyncRuntimeInfo("test-instance-1")
        assertNotNull(stored)
        assertEquals(HostInfo.MAX_RECENT_HOST_INFO, stored.hostInfoList.size)
    }

    @Test
    fun `insertOrUpdateSyncInfo re-advertising same address set does not write`() = runTest {
        // "restamp doesn't churn": re-advertising the same address set (even with a
        // different incoming lastSeen) must not produce a DB write, because
        // hostInfoListEqual compares by address only. Verified via modifyTime stability.
        syncRuntimeInfoDao.insertOrUpdateSyncInfo(testSyncInfo) // hostInfoList = [.100]
        val before = syncRuntimeInfoDao.getSyncRuntimeInfo("test-instance-1")
        assertNotNull(before)

        val reAdvertised = testSyncInfo.copy(
            endpointInfo = testSyncInfo.endpointInfo.copy(
                // same address, deliberately different lastSeen on the wire
                hostInfoList = listOf(HostInfo(32, "192.168.1.100", lastSeen = 999_999L)),
            ),
        )
        syncRuntimeInfoDao.insertOrUpdateSyncInfo(reAdvertised)

        val after = syncRuntimeInfoDao.getSyncRuntimeInfo("test-instance-1")
        assertNotNull(after)
        assertEquals(before.modifyTime, after.modifyTime)
    }

    @Test
    fun `insertOrUpdateSyncInfo LRU-evicts connectHostAddress from list but preserves the column`() = runTest {
        syncRuntimeInfoDao.insertOrUpdateSyncInfo(testSyncInfo)
        val connected = syncRuntimeInfoDao.getSyncRuntimeInfo("test-instance-1")!!.copy(
            connectState = SyncState.CONNECTED,
            connectHostAddress = "192.168.1.100", // == testSyncInfo's only address
            connectNetworkPrefixLength = 32,
        )
        syncRuntimeInfoDao.updateConnectInfo(connected)

        // The peer advertises MAX newer addresses, none of which is .100.
        val newer = (1..HostInfo.MAX_RECENT_HOST_INFO).map { HostInfo(24, "10.0.0.$it") }
        syncRuntimeInfoDao.insertOrUpdateSyncInfo(
            testSyncInfo.copy(endpointInfo = testSyncInfo.endpointInfo.copy(hostInfoList = newer)),
        )

        val after = syncRuntimeInfoDao.getSyncRuntimeInfo("test-instance-1")
        assertNotNull(after)
        // .100 was LRU-evicted from the host list...
        assertEquals(HostInfo.MAX_RECENT_HOST_INFO, after.hostInfoList.size)
        assertTrue(after.hostInfoList.none { it.hostAddress == "192.168.1.100" })
        // ...but the connect address column (and state) are still preserved.
        assertEquals("192.168.1.100", after.connectHostAddress)
        assertEquals(SyncState.CONNECTED, after.connectState)
    }

    @Test
    fun `insertOrUpdateSyncInfo preserves connect address on mDNS-style update`() = runTest {
        // Establish a CONNECTED device with a known connect address.
        syncRuntimeInfoDao.insertOrUpdateSyncInfo(testSyncInfo)
        val connected = syncRuntimeInfoDao.getSyncRuntimeInfo("test-instance-1")!!.copy(
            connectState = SyncState.CONNECTED,
            connectHostAddress = "192.168.1.100",
            connectNetworkPrefixLength = 32,
        )
        syncRuntimeInfoDao.updateConnectInfo(connected)

        // An mDNS re-advertisement adds a new address and carries no connectInfo.
        val advertised = testSyncInfo.copy(
            endpointInfo = testSyncInfo.endpointInfo.copy(
                hostInfoList = listOf(HostInfo(24, "192.168.1.200")),
            ),
        )
        syncRuntimeInfoDao.insertOrUpdateSyncInfo(advertised)

        val after = syncRuntimeInfoDao.getSyncRuntimeInfo("test-instance-1")
        assertNotNull(after)
        // connectState was already preserved via the -1 sentinel; the connect address
        // must be preserved too (#4499 weakness ①) rather than nulled.
        assertEquals(SyncState.CONNECTED, after.connectState)
        assertEquals("192.168.1.100", after.connectHostAddress)
        assertEquals(32.toShort(), after.connectNetworkPrefixLength)
        // ...while the newly-advertised address is merged into the host list.
        assertTrue(after.hostInfoList.any { it.hostAddress == "192.168.1.200" })
    }

    @Test
    fun `insertOrUpdateSyncInfo evicts old ghost addresses as new ones arrive`() = runTest {
        // #4499 ghost accumulation: a full set of old addresses, then a full set of new
        // ones. Capacity-LRU must end with only the newly-advertised addresses.
        val oldHosts = (1..HostInfo.MAX_RECENT_HOST_INFO).map { HostInfo(24, "10.0.0.$it") }
        syncRuntimeInfoDao.insertOrUpdateSyncInfo(
            testSyncInfo.copy(
                endpointInfo = testSyncInfo.endpointInfo.copy(hostInfoList = oldHosts),
            ),
        )

        val newHosts = (1..HostInfo.MAX_RECENT_HOST_INFO).map { HostInfo(24, "10.1.1.$it") }
        syncRuntimeInfoDao.insertOrUpdateSyncInfo(
            testSyncInfo.copy(
                endpointInfo = testSyncInfo.endpointInfo.copy(hostInfoList = newHosts),
            ),
        )

        val stored = syncRuntimeInfoDao.getSyncRuntimeInfo("test-instance-1")
        assertNotNull(stored)
        assertEquals(HostInfo.MAX_RECENT_HOST_INFO, stored.hostInfoList.size)
        assertTrue(stored.hostInfoList.all { it.hostAddress.startsWith("10.1.1.") })
    }
}