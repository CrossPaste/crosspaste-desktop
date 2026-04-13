package com.crosspaste.sync

import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.db.sync.SyncState
import com.crosspaste.net.clientapi.ConnectionRefused
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.net.ws.WsSessionManager
import com.crosspaste.secure.SecureStore
import com.crosspaste.sync.SyncTestFixtures.createConnectedSyncRuntimeInfo
import com.crosspaste.sync.SyncTestFixtures.createSyncRuntimeInfo
import com.crosspaste.sync.SyncTestFixtures.createUnverifiedSyncRuntimeInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncDeviceManagerTest {

    private class TestDeps {
        val secureStore: SecureStore = mockk(relaxed = true)
        val syncClientApi: SyncClientApi = mockk(relaxed = true)
        val syncRuntimeInfoDao: SyncRuntimeInfoDao = mockk(relaxed = true)

        val wsSessionManager: WsSessionManager = mockk(relaxed = true)

        fun createManager(): SyncDeviceManager =
            SyncDeviceManager(
                secureStore = secureStore,
                syncClientApi = syncClientApi,
                syncRuntimeInfoDao = syncRuntimeInfoDao,
                wsSessionManager = wsSessionManager,
            )
    }

    // ========== updateAllowSend / updateAllowReceive / updateNoteName ==========

    @Test
    fun updateAllowSend_delegatesToDao() =
        runTest {
            val deps = TestDeps()
            val manager = deps.createManager()
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo()

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateAllowSend(capture(capturedInfo)) } returns null

            manager.updateAllowSend(syncRuntimeInfo, false)

            assertEquals(false, capturedInfo.captured.allowSend)
        }

    @Test
    fun updateAllowReceive_delegatesToDao() =
        runTest {
            val deps = TestDeps()
            val manager = deps.createManager()
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo()

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateAllowReceive(capture(capturedInfo)) } returns null

            manager.updateAllowReceive(syncRuntimeInfo, false)

            assertEquals(false, capturedInfo.captured.allowReceive)
        }

    @Test
    fun updateNoteName_delegatesToDao() =
        runTest {
            val deps = TestDeps()
            val manager = deps.createManager()
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo()

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateNoteName(capture(capturedInfo)) } returns null

            manager.updateNoteName(syncRuntimeInfo, "My Device")

            assertEquals("My Device", capturedInfo.captured.noteName)
        }

    // ========== showToken ==========

    @Test
    fun showToken_unverifiedAndSuccess_logsSuccess() =
        runTest {
            val deps = TestDeps()
            val manager = deps.createManager()
            val syncRuntimeInfo = createUnverifiedSyncRuntimeInfo()

            coEvery { deps.syncClientApi.showToken(any()) } returns SuccessResult(true)

            manager.showToken(syncRuntimeInfo)

            coVerify { deps.syncClientApi.showToken(any()) }
            coVerify(exactly = 0) { deps.syncRuntimeInfoDao.updateConnectInfo(any()) }
        }

    @Test
    fun showToken_unverifiedAndFails_setsDisconnected() =
        runTest {
            val deps = TestDeps()
            val manager = deps.createManager()
            val syncRuntimeInfo = createUnverifiedSyncRuntimeInfo()

            coEvery { deps.syncClientApi.showToken(any()) } returns ConnectionRefused

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            manager.showToken(syncRuntimeInfo)

            assertEquals(SyncState.DISCONNECTED, capturedInfo.captured.connectState)
        }

    @Test
    fun showToken_notUnverified_doesNothing() =
        runTest {
            val deps = TestDeps()
            val manager = deps.createManager()
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo()

            manager.showToken(syncRuntimeInfo)

            coVerify(exactly = 0) { deps.syncClientApi.showToken(any()) }
        }

    // ========== notifyExit ==========

    @Test
    fun notifyExit_connected_callsNotifyExit() =
        runTest {
            val deps = TestDeps()
            val manager = deps.createManager()
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo()

            manager.notifyExit(syncRuntimeInfo)

            coVerify { deps.syncClientApi.notifyExit(any()) }
        }

    @Test
    fun notifyExit_notConnected_doesNotCallNotifyExit() =
        runTest {
            val deps = TestDeps()
            val manager = deps.createManager()
            val syncRuntimeInfo = createSyncRuntimeInfo(connectState = SyncState.DISCONNECTED)

            manager.notifyExit(syncRuntimeInfo)

            coVerify(exactly = 0) { deps.syncClientApi.notifyExit(any()) }
        }

    // ========== markExit ==========

    @Test
    fun markExit_setsDisconnected() =
        runTest {
            val deps = TestDeps()
            val manager = deps.createManager()
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo()

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            manager.markExit(syncRuntimeInfo)

            assertEquals(SyncState.DISCONNECTED, capturedInfo.captured.connectState)
        }

    // ========== removeDevice ==========

    @Test
    fun removeDevice_deletesKeyAndDbAndNotifiesRemove() =
        runTest {
            val deps = TestDeps()
            val manager = deps.createManager()
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo()

            manager.removeDevice(syncRuntimeInfo)

            coVerify { deps.secureStore.deleteCryptPublicKey(syncRuntimeInfo.appInstanceId) }
            coVerify { deps.syncRuntimeInfoDao.deleteSyncRuntimeInfo(syncRuntimeInfo.appInstanceId) }
            coVerify { deps.syncClientApi.notifyRemove(any()) }
        }

    @Test
    fun removeDevice_noConnectHostAddress_doesNotNotifyRemove() =
        runTest {
            val deps = TestDeps()
            val manager = deps.createManager()
            val syncRuntimeInfo =
                createSyncRuntimeInfo(
                    connectState = SyncState.CONNECTED,
                    connectHostAddress = null,
                )

            manager.removeDevice(syncRuntimeInfo)

            coVerify { deps.secureStore.deleteCryptPublicKey(syncRuntimeInfo.appInstanceId) }
            coVerify { deps.syncRuntimeInfoDao.deleteSyncRuntimeInfo(syncRuntimeInfo.appInstanceId) }
            coVerify(exactly = 0) { deps.syncClientApi.notifyRemove(any()) }
        }
}
