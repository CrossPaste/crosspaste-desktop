package com.crosspaste.sync

import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.db.sync.SyncState
import com.crosspaste.dto.secure.KeyExchangeResponse
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    // ========== cancelPairing ==========

    @Test
    fun cancelPairing_recordedExchange_sendsGenerationMatchedCancel() =
        runTest {
            val deps = TestDeps()
            val manager = deps.createManager()
            val syncRuntimeInfo = createUnverifiedSyncRuntimeInfo()

            coEvery { deps.syncClientApi.trustV2Cancel(any(), any()) } returns SuccessResult(true)

            manager.rememberPendingExchange(syncRuntimeInfo.appInstanceId, 42L)
            manager.cancelPairing(syncRuntimeInfo, requestedAt = Long.MAX_VALUE)

            coVerify(exactly = 1) { deps.syncClientApi.trustV2Cancel(42L, any()) }
            coVerify(exactly = 0) { deps.syncRuntimeInfoDao.updateConnectInfo(any()) }
        }

    @Test
    fun cancelPairing_noRecordedExchange_doesNothing() =
        runTest {
            val deps = TestDeps()
            val manager = deps.createManager()
            val syncRuntimeInfo = createUnverifiedSyncRuntimeInfo()

            manager.cancelPairing(syncRuntimeInfo, requestedAt = Long.MAX_VALUE)

            coVerify(exactly = 0) { deps.syncClientApi.trustV2Cancel(any(), any()) }
        }

    @Test
    fun cancelPairing_disconnectedPeerWithRecord_stillSends() =
        runTest {
            val deps = TestDeps()
            val manager = deps.createManager()
            // The exchange request may have landed while its response was lost:
            // the peer drops to DISCONNECTED but the responder-side entry still
            // exists, so ownership — not connect state — gates the cancel.
            val syncRuntimeInfo =
                createSyncRuntimeInfo(
                    connectState = SyncState.DISCONNECTED,
                    connectHostAddress = "192.168.1.100",
                )

            coEvery { deps.syncClientApi.trustV2Cancel(any(), any()) } returns SuccessResult(true)

            manager.rememberPendingExchange(syncRuntimeInfo.appInstanceId, 42L)
            manager.cancelPairing(syncRuntimeInfo, requestedAt = Long.MAX_VALUE)

            coVerify(exactly = 1) { deps.syncClientApi.trustV2Cancel(42L, any()) }
        }

    @Test
    fun cancelPairing_supersededByNewerExchange_skips() =
        runTest {
            val deps = TestDeps()
            val manager = deps.createManager()
            val syncRuntimeInfo = createUnverifiedSyncRuntimeInfo()

            // The exchange was recorded AFTER the abandon instant (requestedAt=0):
            // it belongs to a reopened dialog and must survive the stale cancel.
            manager.rememberPendingExchange(syncRuntimeInfo.appInstanceId, 42L)
            manager.cancelPairing(syncRuntimeInfo, requestedAt = 0L)

            coVerify(exactly = 0) { deps.syncClientApi.trustV2Cancel(any(), any()) }
        }

    @Test
    fun cancelPairing_afterClearPendingExchange_doesNothing() =
        runTest {
            val deps = TestDeps()
            val manager = deps.createManager()
            val syncRuntimeInfo = createUnverifiedSyncRuntimeInfo()

            // A successful confirm clears the record — nothing left to cancel.
            manager.rememberPendingExchange(syncRuntimeInfo.appInstanceId, 42L)
            manager.clearPendingExchange(syncRuntimeInfo.appInstanceId)
            manager.cancelPairing(syncRuntimeInfo, requestedAt = Long.MAX_VALUE)

            coVerify(exactly = 0) { deps.syncClientApi.trustV2Cancel(any(), any()) }
        }

    @Test
    fun cancelPairing_consumesRecordOnSend() =
        runTest {
            val deps = TestDeps()
            val manager = deps.createManager()
            val syncRuntimeInfo = createUnverifiedSyncRuntimeInfo()

            coEvery { deps.syncClientApi.trustV2Cancel(any(), any()) } returns SuccessResult(true)

            manager.rememberPendingExchange(syncRuntimeInfo.appInstanceId, 42L)
            manager.cancelPairing(syncRuntimeInfo, requestedAt = Long.MAX_VALUE)
            manager.cancelPairing(syncRuntimeInfo, requestedAt = Long.MAX_VALUE)

            coVerify(exactly = 1) { deps.syncClientApi.trustV2Cancel(any(), any()) }
        }

    @Test
    fun cancelPairing_noConnectHostAddress_doesNothing() =
        runTest {
            val deps = TestDeps()
            val manager = deps.createManager()
            val syncRuntimeInfo =
                createSyncRuntimeInfo(
                    connectState = SyncState.UNVERIFIED,
                    connectHostAddress = null,
                )

            manager.rememberPendingExchange(syncRuntimeInfo.appInstanceId, 42L)
            manager.cancelPairing(syncRuntimeInfo, requestedAt = Long.MAX_VALUE)

            coVerify(exactly = 0) { deps.syncClientApi.trustV2Cancel(any(), any()) }
        }

    @Test
    fun cancelPairing_failure_isBestEffortWithoutStateChange() =
        runTest {
            val deps = TestDeps()
            val manager = deps.createManager()
            val syncRuntimeInfo = createUnverifiedSyncRuntimeInfo()

            coEvery { deps.syncClientApi.trustV2Cancel(any(), any()) } returns ConnectionRefused

            manager.rememberPendingExchange(syncRuntimeInfo.appInstanceId, 42L)
            manager.cancelPairing(syncRuntimeInfo, requestedAt = Long.MAX_VALUE)

            coVerify(exactly = 0) { deps.syncRuntimeInfoDao.updateConnectInfo(any()) }
        }

    @Test
    fun exchangeKeysForPairing_success_recordsExchangeForCancel() =
        runTest {
            val deps = TestDeps()
            val manager = deps.createManager()
            val syncRuntimeInfo = createUnverifiedSyncRuntimeInfo()
            val response =
                KeyExchangeResponse(
                    signPublicKey = byteArrayOf(1),
                    cryptPublicKey = byteArrayOf(2),
                    timestamp = 42L,
                    signature = byteArrayOf(3),
                )

            coEvery { deps.syncClientApi.exchangeKeys(any(), any()) } returns SuccessResult(response)
            coEvery { deps.syncClientApi.trustV2Cancel(any(), any()) } returns SuccessResult(true)

            manager.exchangeKeysForPairing(syncRuntimeInfo)
            manager.cancelPairing(syncRuntimeInfo, requestedAt = Long.MAX_VALUE)

            coVerify(exactly = 1) { deps.syncClientApi.trustV2Cancel(42L, any()) }
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

    // ========== showPairingCode ==========

    @Test
    fun showPairingCode_unverifiedAndSuccess_returnsTrue() =
        runTest {
            val deps = TestDeps()
            val manager = deps.createManager()
            val syncRuntimeInfo = createUnverifiedSyncRuntimeInfo()
            coEvery { deps.syncClientApi.showPairingCode(any()) } returns SuccessResult(true)

            assertTrue(manager.showPairingCode(syncRuntimeInfo))

            coVerify(exactly = 1) { deps.syncClientApi.showPairingCode(any()) }
            coVerify(exactly = 0) { deps.syncRuntimeInfoDao.updateConnectInfo(any()) }
        }

    @Test
    fun showPairingCode_unverifiedAndFails_returnsFalseAndDisconnects() =
        runTest {
            val deps = TestDeps()
            val manager = deps.createManager()
            val syncRuntimeInfo = createUnverifiedSyncRuntimeInfo()
            coEvery { deps.syncClientApi.showPairingCode(any()) } returns ConnectionRefused
            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            assertFalse(manager.showPairingCode(syncRuntimeInfo))

            assertEquals(SyncState.DISCONNECTED, capturedInfo.captured.connectState)
        }

    @Test
    fun showPairingCode_notUnverified_returnsFalseWithoutRequest() =
        runTest {
            val deps = TestDeps()
            val manager = deps.createManager()
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo()

            assertFalse(manager.showPairingCode(syncRuntimeInfo))

            coVerify(exactly = 0) { deps.syncClientApi.showPairingCode(any()) }
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
