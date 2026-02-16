package com.crosspaste.sync

import com.crosspaste.app.RatingPromptManager
import com.crosspaste.db.sync.HostInfo
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.db.sync.SyncState
import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.NetworkInterfaceService
import com.crosspaste.net.PasteBonjourService
import com.crosspaste.net.SyncInfoFactory
import com.crosspaste.net.TelnetHelper
import com.crosspaste.net.VersionRelation
import com.crosspaste.net.clientapi.ConnectionRefused
import com.crosspaste.net.clientapi.DecryptFail
import com.crosspaste.net.clientapi.EncryptFail
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.net.clientapi.UnknownError
import com.crosspaste.secure.SecureStore
import com.crosspaste.sync.SyncTestFixtures.createConnectedSyncRuntimeInfo
import com.crosspaste.sync.SyncTestFixtures.createConnectingSyncRuntimeInfo
import com.crosspaste.sync.SyncTestFixtures.createSyncRuntimeInfo
import com.crosspaste.sync.SyncTestFixtures.createUnverifiedSyncRuntimeInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncResolverTest {

    private class TestDeps {
        val pasteBonjourService: PasteBonjourService = mockk(relaxed = true)
        val networkInterfaceService: NetworkInterfaceService = mockk(relaxed = true)
        val ratingPromptManager: RatingPromptManager = mockk(relaxed = true)
        val secureStore: SecureStore = mockk(relaxed = true)
        val syncClientApi: SyncClientApi = mockk(relaxed = true)
        val syncInfoFactory: SyncInfoFactory = mockk(relaxed = true)
        val syncRuntimeInfoDao: SyncRuntimeInfoDao = mockk(relaxed = true)
        val telnetHelper: TelnetHelper = mockk(relaxed = true)
        val tokenCache: FakeTokenCache = FakeTokenCache()

        fun stubDbRead(syncRuntimeInfo: SyncRuntimeInfo) {
            coEvery { syncRuntimeInfoDao.getSyncRuntimeInfo(syncRuntimeInfo.appInstanceId) } returns syncRuntimeInfo
        }

        fun createResolver(): SyncResolver =
            SyncResolver(
                lazyPasteBonjourService = lazy { pasteBonjourService },
                networkInterfaceService = networkInterfaceService,
                ratingPromptManager = ratingPromptManager,
                secureStore = secureStore,
                syncClientApi = syncClientApi,
                syncInfoFactory = syncInfoFactory,
                syncRuntimeInfoDao = syncRuntimeInfoDao,
                telnetHelper = telnetHelper,
                tokenCache = tokenCache,
            )
    }

    private class FakeTokenCache : TokenCacheApi {
        private val tokens = mutableMapOf<String, Int>()

        override fun setToken(
            appInstanceId: String,
            token: Int,
        ) {
            tokens[appInstanceId] = token
        }

        override fun getToken(appInstanceId: String): Int? = tokens.remove(appInstanceId)
    }

    // ========== A. resolveDisconnected ==========

    @Test
    fun resolveDisconnected_switchHostReturnsEqualTo_setsConnecting() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo =
                createSyncRuntimeInfo(
                    hostInfoList = listOf(HostInfo(24, "192.168.1.100")),
                )
            val hostInfo = HostInfo(24, "192.168.1.100")

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.telnetHelper.switchHost(any(), any(), any()) } returns
                Pair(hostInfo, VersionRelation.EQUAL_TO)

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            val callback = createTestCallback()
            resolver.emitEvent(SyncEvent.ResolveDisconnected(syncRuntimeInfo, callback))

            assertEquals(SyncState.CONNECTING, capturedInfo.captured.connectState)
            assertEquals("192.168.1.100", capturedInfo.captured.connectHostAddress)
        }

    @Test
    fun resolveDisconnected_switchHostReturnsLowerThan_setsIncompatible() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createSyncRuntimeInfo()
            val hostInfo = HostInfo(24, "192.168.1.100")

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.telnetHelper.switchHost(any(), any(), any()) } returns
                Pair(hostInfo, VersionRelation.LOWER_THAN)

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            val callback = createTestCallback()
            resolver.emitEvent(SyncEvent.ResolveDisconnected(syncRuntimeInfo, callback))

            assertEquals(SyncState.INCOMPATIBLE, capturedInfo.captured.connectState)
        }

    @Test
    fun resolveDisconnected_switchHostReturnsHigherThan_setsIncompatible() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createSyncRuntimeInfo()
            val hostInfo = HostInfo(24, "192.168.1.100")

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.telnetHelper.switchHost(any(), any(), any()) } returns
                Pair(hostInfo, VersionRelation.HIGHER_THAN)

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            val callback = createTestCallback()
            resolver.emitEvent(SyncEvent.ResolveDisconnected(syncRuntimeInfo, callback))

            assertEquals(SyncState.INCOMPATIBLE, capturedInfo.captured.connectState)
        }

    @Test
    fun resolveDisconnected_switchHostReturnsNull_staysDisconnected() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.telnetHelper.switchHost(any(), any(), any()) } returns null

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            val callback = createTestCallback()
            resolver.emitEvent(SyncEvent.ResolveDisconnected(syncRuntimeInfo, callback))

            assertEquals(SyncState.DISCONNECTED, capturedInfo.captured.connectState)
        }

    @Test
    fun resolveDisconnected_emptyHostInfoList_switchHostCalledWithEmptyList() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createSyncRuntimeInfo(hostInfoList = emptyList())

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.telnetHelper.switchHost(any(), any(), any()) } returns null

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            val callback = createTestCallback()
            resolver.emitEvent(SyncEvent.ResolveDisconnected(syncRuntimeInfo, callback))

            coVerify { deps.telnetHelper.switchHost(emptyList(), any(), any()) }
            assertEquals(SyncState.DISCONNECTED, capturedInfo.captured.connectState)
        }

    // ========== B. resolveConnecting ==========

    @Test
    fun resolveConnecting_hasCryptoKeyAndHeartbeatConnected_setsConnected() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectingSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.secureStore.existCryptPublicKey(any()) } returns true
            coEvery { deps.syncClientApi.heartbeat(any(), any(), any()) } returns
                SuccessResult(VersionRelation.EQUAL_TO)

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            val callback = createTestCallback()
            resolver.emitEvent(SyncEvent.ResolveConnecting(syncRuntimeInfo, callback))

            assertEquals(SyncState.CONNECTED, capturedInfo.captured.connectState)
            verify { deps.ratingPromptManager.trackSignificantAction() }
        }

    @Test
    fun resolveConnecting_hasCryptoKeyAndHeartbeatUnmatched_deletesKeyAndTriesTokenCache() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectingSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.secureStore.existCryptPublicKey(any()) } returns true
            coEvery {
                deps.syncClientApi.heartbeat(
                    syncInfo = isNull(),
                    targetAppInstanceId = any(),
                    toUrl = any(),
                )
            } returns DecryptFail

            // No token cached, telnet returns null -> DISCONNECTED
            coEvery { deps.telnetHelper.telnet(any(), any(), any()) } returns null

            val capturedInfos = mutableListOf<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfos)) } returns "test-app-1"

            val callback = createTestCallback()
            resolver.emitEvent(SyncEvent.ResolveConnecting(syncRuntimeInfo, callback))

            coVerify { deps.secureStore.deleteCryptPublicKey(syncRuntimeInfo.appInstanceId) }
        }

    @Test
    fun resolveConnecting_hasCryptoKeyAndHeartbeatIncompatible_setsIncompatible() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectingSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.secureStore.existCryptPublicKey(any()) } returns true
            coEvery { deps.syncClientApi.heartbeat(any(), any(), any()) } returns
                SuccessResult(VersionRelation.LOWER_THAN)

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            val callback = createTestCallback()
            resolver.emitEvent(SyncEvent.ResolveConnecting(syncRuntimeInfo, callback))

            assertEquals(SyncState.INCOMPATIBLE, capturedInfo.captured.connectState)
        }

    @Test
    fun resolveConnecting_noCryptoKey_callsTryUseTokenCache() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectingSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.secureStore.existCryptPublicKey(any()) } returns false
            // No token cached -> telnet
            coEvery { deps.telnetHelper.telnet(any(), any(), any()) } returns VersionRelation.EQUAL_TO

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            val callback = createTestCallback()
            resolver.emitEvent(SyncEvent.ResolveConnecting(syncRuntimeInfo, callback))

            assertEquals(SyncState.UNVERIFIED, capturedInfo.captured.connectState)
        }

    @Test
    fun resolveConnecting_noConnectHostAddress_setsDisconnected() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo =
                createSyncRuntimeInfo(
                    connectState = SyncState.CONNECTING,
                    connectHostAddress = null,
                )

            deps.stubDbRead(syncRuntimeInfo)

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            val callback = createTestCallback()
            resolver.emitEvent(SyncEvent.ResolveConnecting(syncRuntimeInfo, callback))

            assertEquals(SyncState.DISCONNECTED, capturedInfo.captured.connectState)
        }

    @Test
    fun resolveConnecting_heartbeatConnectionRefused_setsDisconnected() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectingSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.secureStore.existCryptPublicKey(any()) } returns true
            coEvery { deps.syncClientApi.heartbeat(any(), any(), any()) } returns ConnectionRefused

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            val callback = createTestCallback()
            resolver.emitEvent(SyncEvent.ResolveConnecting(syncRuntimeInfo, callback))

            assertEquals(SyncState.DISCONNECTED, capturedInfo.captured.connectState)
        }

    // ========== C. heartbeat result mapping ==========

    @Test
    fun heartbeat_successWithEqualTo_returnsConnected() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectingSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.secureStore.existCryptPublicKey(any()) } returns true
            coEvery { deps.syncClientApi.heartbeat(any(), any(), any()) } returns
                SuccessResult(VersionRelation.EQUAL_TO)

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            resolver.emitEvent(SyncEvent.ResolveConnecting(syncRuntimeInfo, createTestCallback()))

            assertEquals(SyncState.CONNECTED, capturedInfo.captured.connectState)
        }

    @Test
    fun heartbeat_successWithLowerThan_returnsIncompatible() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectingSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.secureStore.existCryptPublicKey(any()) } returns true
            coEvery { deps.syncClientApi.heartbeat(any(), any(), any()) } returns
                SuccessResult(VersionRelation.LOWER_THAN)

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            resolver.emitEvent(SyncEvent.ResolveConnecting(syncRuntimeInfo, createTestCallback()))

            assertEquals(SyncState.INCOMPATIBLE, capturedInfo.captured.connectState)
        }

    @Test
    fun heartbeat_successWithHigherThan_returnsIncompatible() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectingSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.secureStore.existCryptPublicKey(any()) } returns true
            coEvery { deps.syncClientApi.heartbeat(any(), any(), any()) } returns
                SuccessResult(VersionRelation.HIGHER_THAN)

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            resolver.emitEvent(SyncEvent.ResolveConnecting(syncRuntimeInfo, createTestCallback()))

            assertEquals(SyncState.INCOMPATIBLE, capturedInfo.captured.connectState)
        }

    @Test
    fun heartbeat_successWithNull_returnsDisconnected() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectingSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.secureStore.existCryptPublicKey(any()) } returns true
            coEvery { deps.syncClientApi.heartbeat(any(), any(), any()) } returns
                SuccessResult(null)

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            resolver.emitEvent(SyncEvent.ResolveConnecting(syncRuntimeInfo, createTestCallback()))

            assertEquals(SyncState.DISCONNECTED, capturedInfo.captured.connectState)
        }

    @Test
    fun heartbeat_failureNotMatchAppInstanceId_returnsDisconnected() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectingSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.secureStore.existCryptPublicKey(any()) } returns true
            coEvery { deps.syncClientApi.heartbeat(any(), any(), any()) } returns
                FailureResult(
                    PasteException(
                        StandardErrorCode.NOT_MATCH_APP_INSTANCE_ID.toErrorCode(),
                        "not match",
                    ),
                )

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            resolver.emitEvent(SyncEvent.ResolveConnecting(syncRuntimeInfo, createTestCallback()))

            assertEquals(SyncState.DISCONNECTED, capturedInfo.captured.connectState)
        }

    @Test
    fun heartbeat_failureDecryptFail_returnsUnmatched() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectingSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.secureStore.existCryptPublicKey(any()) } returns true
            coEvery { deps.syncClientApi.heartbeat(any(), any(), any()) } returns
                FailureResult(
                    PasteException(
                        StandardErrorCode.DECRYPT_FAIL.toErrorCode(),
                        "decrypt fail",
                    ),
                )

            // After UNMATCHED: deletes key, tries token cache
            coEvery { deps.telnetHelper.telnet(any(), any(), any()) } returns null

            val capturedInfos = mutableListOf<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfos)) } returns "test-app-1"

            resolver.emitEvent(SyncEvent.ResolveConnecting(syncRuntimeInfo, createTestCallback()))

            coVerify { deps.secureStore.deleteCryptPublicKey(any()) }
        }

    @Test
    fun heartbeat_encryptFail_returnsUnmatched() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectingSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.secureStore.existCryptPublicKey(any()) } returns true
            coEvery { deps.syncClientApi.heartbeat(any(), any(), any()) } returns EncryptFail

            coEvery { deps.telnetHelper.telnet(any(), any(), any()) } returns null

            val capturedInfos = mutableListOf<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfos)) } returns "test-app-1"

            resolver.emitEvent(SyncEvent.ResolveConnecting(syncRuntimeInfo, createTestCallback()))

            coVerify { deps.secureStore.deleteCryptPublicKey(any()) }
        }

    @Test
    fun heartbeat_decryptFail_returnsUnmatched() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectingSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.secureStore.existCryptPublicKey(any()) } returns true
            coEvery { deps.syncClientApi.heartbeat(any(), any(), any()) } returns DecryptFail

            coEvery { deps.telnetHelper.telnet(any(), any(), any()) } returns null

            val capturedInfos = mutableListOf<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfos)) } returns "test-app-1"

            resolver.emitEvent(SyncEvent.ResolveConnecting(syncRuntimeInfo, createTestCallback()))

            coVerify { deps.secureStore.deleteCryptPublicKey(any()) }
        }

    @Test
    fun heartbeat_unknownError_returnsDisconnected() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectingSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.secureStore.existCryptPublicKey(any()) } returns true
            coEvery { deps.syncClientApi.heartbeat(any(), any(), any()) } returns UnknownError

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            resolver.emitEvent(SyncEvent.ResolveConnecting(syncRuntimeInfo, createTestCallback()))

            assertEquals(SyncState.DISCONNECTED, capturedInfo.captured.connectState)
        }

    // ========== D. tryUseTokenCache ==========

    @Test
    fun tryUseTokenCache_tokenCachedAndTrustSucceeds_setsConnected() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectingSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)
            deps.tokenCache.setToken(syncRuntimeInfo.appInstanceId, 123456)

            coEvery { deps.secureStore.existCryptPublicKey(any()) } returns false
            coEvery { deps.syncClientApi.trust(any(), any(), any(), any()) } returns SuccessResult(true)
            coEvery { deps.syncClientApi.heartbeat(any(), any(), any()) } returns
                SuccessResult(VersionRelation.EQUAL_TO)
            coEvery { deps.syncInfoFactory.createSyncInfo(any()) } returns
                SyncTestFixtures.createSyncInfo()
            coEvery { deps.networkInterfaceService.getCurrentUseNetworkInterfaces() } returns emptyList()

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            resolver.emitEvent(SyncEvent.ResolveConnecting(syncRuntimeInfo, createTestCallback()))

            assertEquals(SyncState.CONNECTED, capturedInfo.captured.connectState)
            verify { deps.ratingPromptManager.trackSignificantAction() }
        }

    @Test
    fun tryUseTokenCache_tokenCachedButTrustFails_fallsBackToTelnet() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectingSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)
            deps.tokenCache.setToken(syncRuntimeInfo.appInstanceId, 123456)

            coEvery { deps.secureStore.existCryptPublicKey(any()) } returns false
            coEvery { deps.syncClientApi.trust(any(), any(), any(), any()) } returns
                FailureResult(PasteException(StandardErrorCode.TOKEN_INVALID.toErrorCode(), "invalid"))
            coEvery { deps.telnetHelper.telnet(any(), any(), any()) } returns VersionRelation.EQUAL_TO

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            resolver.emitEvent(SyncEvent.ResolveConnecting(syncRuntimeInfo, createTestCallback()))

            assertEquals(SyncState.UNVERIFIED, capturedInfo.captured.connectState)
        }

    @Test
    fun tryUseTokenCache_noTokenAndTelnetEqualTo_setsUnverified() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectingSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.secureStore.existCryptPublicKey(any()) } returns false
            coEvery { deps.telnetHelper.telnet(any(), any(), any()) } returns VersionRelation.EQUAL_TO

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            resolver.emitEvent(SyncEvent.ResolveConnecting(syncRuntimeInfo, createTestCallback()))

            assertEquals(SyncState.UNVERIFIED, capturedInfo.captured.connectState)
        }

    @Test
    fun tryUseTokenCache_noTokenAndTelnetNonEqual_setsIncompatible() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectingSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.secureStore.existCryptPublicKey(any()) } returns false
            coEvery { deps.telnetHelper.telnet(any(), any(), any()) } returns VersionRelation.LOWER_THAN

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            resolver.emitEvent(SyncEvent.ResolveConnecting(syncRuntimeInfo, createTestCallback()))

            assertEquals(SyncState.INCOMPATIBLE, capturedInfo.captured.connectState)
        }

    @Test
    fun tryUseTokenCache_noTokenAndTelnetNull_setsDisconnected() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectingSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.secureStore.existCryptPublicKey(any()) } returns false
            coEvery { deps.telnetHelper.telnet(any(), any(), any()) } returns null

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            resolver.emitEvent(SyncEvent.ResolveConnecting(syncRuntimeInfo, createTestCallback()))

            assertEquals(SyncState.DISCONNECTED, capturedInfo.captured.connectState)
        }

    @Test
    fun tryUseTokenCache_noConnectHostAddress_setsDisconnected() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo =
                createSyncRuntimeInfo(
                    connectState = SyncState.CONNECTING,
                    connectHostAddress = null,
                )

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.secureStore.existCryptPublicKey(any()) } returns false

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            resolver.emitEvent(SyncEvent.ResolveConnecting(syncRuntimeInfo, createTestCallback()))

            assertEquals(SyncState.DISCONNECTED, capturedInfo.captured.connectState)
        }

    // ========== E. trustByToken ==========

    @Test
    fun trustByToken_unverifiedAndTrustSucceeds_callbackTrueAndConnected() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createUnverifiedSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.syncClientApi.trust(any(), any(), any(), any()) } returns SuccessResult(true)
            coEvery { deps.syncClientApi.heartbeat(any(), any(), any()) } returns
                SuccessResult(VersionRelation.EQUAL_TO)
            coEvery { deps.syncInfoFactory.createSyncInfo(any()) } returns SyncTestFixtures.createSyncInfo()
            coEvery { deps.networkInterfaceService.getCurrentUseNetworkInterfaces() } returns emptyList()

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            var callbackResult: Boolean? = null
            resolver.emitEvent(
                SyncEvent.TrustByToken(syncRuntimeInfo, 123456) { callbackResult = it },
            )

            assertEquals(true, callbackResult)
            assertEquals(SyncState.CONNECTED, capturedInfo.captured.connectState)
            verify { deps.ratingPromptManager.trackSignificantAction() }
        }

    @Test
    fun trustByToken_unverifiedAndTrustFails_callbackFalse() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createUnverifiedSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.syncClientApi.trust(any(), any(), any(), any()) } returns
                FailureResult(PasteException(StandardErrorCode.TOKEN_INVALID.toErrorCode(), "invalid"))

            var callbackResult: Boolean? = null
            resolver.emitEvent(
                SyncEvent.TrustByToken(syncRuntimeInfo, 123456) { callbackResult = it },
            )

            assertEquals(false, callbackResult)
        }

    @Test
    fun trustByToken_notUnverified_callbackFalse() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)

            var callbackResult: Boolean? = null
            resolver.emitEvent(
                SyncEvent.TrustByToken(syncRuntimeInfo, 123456) { callbackResult = it },
            )

            assertEquals(false, callbackResult)
        }

    @Test
    fun trustByToken_unverifiedNoConnectHostAddress_callbackFalse() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo =
                createSyncRuntimeInfo(
                    connectState = SyncState.UNVERIFIED,
                    connectHostAddress = null,
                )

            deps.stubDbRead(syncRuntimeInfo)

            var callbackResult: Boolean? = null
            resolver.emitEvent(
                SyncEvent.TrustByToken(syncRuntimeInfo, 123456) { callbackResult = it },
            )

            assertEquals(false, callbackResult)
        }

    // ========== F. Simple delegate events ==========

    @Test
    fun updateAllowSend_delegatesToDao() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateAllowSend(capture(capturedInfo)) } returns null

            resolver.emitEvent(SyncEvent.UpdateAllowSend(syncRuntimeInfo, false))

            assertEquals(false, capturedInfo.captured.allowSend)
        }

    @Test
    fun updateAllowReceive_delegatesToDao() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateAllowReceive(capture(capturedInfo)) } returns null

            resolver.emitEvent(SyncEvent.UpdateAllowReceive(syncRuntimeInfo, false))

            assertEquals(false, capturedInfo.captured.allowReceive)
        }

    @Test
    fun updateNoteName_delegatesToDao() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateNoteName(capture(capturedInfo)) } returns null

            resolver.emitEvent(SyncEvent.UpdateNoteName(syncRuntimeInfo, "My Device"))

            assertEquals("My Device", capturedInfo.captured.noteName)
        }

    @Test
    fun showToken_unverifiedAndSuccess_logsSuccess() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createUnverifiedSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.syncClientApi.showToken(any()) } returns SuccessResult(true)

            resolver.emitEvent(SyncEvent.ShowToken(syncRuntimeInfo))

            coVerify { deps.syncClientApi.showToken(any()) }
            coVerify(exactly = 0) { deps.syncRuntimeInfoDao.updateConnectInfo(any()) }
        }

    @Test
    fun showToken_unverifiedAndFails_setsDisconnected() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createUnverifiedSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.syncClientApi.showToken(any()) } returns ConnectionRefused

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            resolver.emitEvent(SyncEvent.ShowToken(syncRuntimeInfo))

            assertEquals(SyncState.DISCONNECTED, capturedInfo.captured.connectState)
        }

    @Test
    fun showToken_notUnverified_doesNothing() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)

            resolver.emitEvent(SyncEvent.ShowToken(syncRuntimeInfo))

            coVerify(exactly = 0) { deps.syncClientApi.showToken(any()) }
        }

    @Test
    fun notifyExit_connected_callsNotifyExit() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)

            resolver.emitEvent(SyncEvent.NotifyExit(syncRuntimeInfo))

            coVerify { deps.syncClientApi.notifyExit(any()) }
        }

    @Test
    fun notifyExit_notConnected_doesNotCallNotifyExit() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createSyncRuntimeInfo(connectState = SyncState.DISCONNECTED)

            deps.stubDbRead(syncRuntimeInfo)

            resolver.emitEvent(SyncEvent.NotifyExit(syncRuntimeInfo))

            coVerify(exactly = 0) { deps.syncClientApi.notifyExit(any()) }
        }

    @Test
    fun markExit_setsDisconnected() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            resolver.emitEvent(SyncEvent.MarkExit(syncRuntimeInfo))

            assertEquals(SyncState.DISCONNECTED, capturedInfo.captured.connectState)
        }

    @Test
    fun removeDevice_deletesKeyAndDbAndNotifiesRemove() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)

            resolver.emitEvent(SyncEvent.RemoveDevice(syncRuntimeInfo))

            coVerify { deps.secureStore.deleteCryptPublicKey(syncRuntimeInfo.appInstanceId) }
            coVerify { deps.syncRuntimeInfoDao.deleteSyncRuntimeInfo(syncRuntimeInfo.appInstanceId) }
            coVerify { deps.syncClientApi.notifyRemove(any()) }
        }

    @Test
    fun removeDevice_noConnectHostAddress_doesNotNotifyRemove() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo =
                createSyncRuntimeInfo(
                    connectState = SyncState.CONNECTED,
                    connectHostAddress = null,
                )

            deps.stubDbRead(syncRuntimeInfo)

            resolver.emitEvent(SyncEvent.RemoveDevice(syncRuntimeInfo))

            coVerify { deps.secureStore.deleteCryptPublicKey(syncRuntimeInfo.appInstanceId) }
            coVerify { deps.syncRuntimeInfoDao.deleteSyncRuntimeInfo(syncRuntimeInfo.appInstanceId) }
            coVerify(exactly = 0) { deps.syncClientApi.notifyRemove(any()) }
        }

    // ========== G. Event dispatch and callback ==========

    @Test
    fun resolveConnection_disconnected_dispatchesToResolveDisconnected() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createSyncRuntimeInfo(connectState = SyncState.DISCONNECTED)

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.telnetHelper.switchHost(any(), any(), any()) } returns null

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            val callback = createTestCallback()
            resolver.emitEvent(SyncEvent.ResolveConnection(syncRuntimeInfo, callback))

            coVerify { deps.telnetHelper.switchHost(any(), any(), any()) }
        }

    @Test
    fun resolveConnection_incompatible_dispatchesToResolveDisconnected() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createSyncRuntimeInfo(connectState = SyncState.INCOMPATIBLE)

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.telnetHelper.switchHost(any(), any(), any()) } returns null

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            val callback = createTestCallback()
            resolver.emitEvent(SyncEvent.ResolveConnection(syncRuntimeInfo, callback))

            coVerify { deps.telnetHelper.switchHost(any(), any(), any()) }
        }

    @Test
    fun resolveConnection_connecting_dispatchesToResolveConnecting() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectingSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.secureStore.existCryptPublicKey(any()) } returns false
            coEvery { deps.telnetHelper.telnet(any(), any(), any()) } returns null

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            val callback = createTestCallback()
            resolver.emitEvent(SyncEvent.ResolveConnection(syncRuntimeInfo, callback))

            // resolveConnecting path is taken, which calls telnet (not switchHost)
            coVerify(exactly = 0) { deps.telnetHelper.switchHost(any(), any(), any()) }
        }

    @Test
    fun forceResolveConnection_callsRefreshThenResolve() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createSyncRuntimeInfo(connectState = SyncState.DISCONNECTED)

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.telnetHelper.switchHost(any(), any(), any()) } returns null
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(any()) } returns "test-app-1"

            val callback = createTestCallback()
            resolver.emitEvent(SyncEvent.ForceResolveConnection(syncRuntimeInfo, callback))

            verify { deps.pasteBonjourService.refreshTarget(any(), any()) }
            coVerify { deps.telnetHelper.switchHost(any(), any(), any()) }
        }

    @Test
    fun refreshSyncInfo_delegatesToBonjour() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val hostInfoList = listOf(HostInfo(24, "192.168.1.100"))

            resolver.emitEvent(SyncEvent.RefreshSyncInfo("test-app-1", hostInfoList))

            verify { deps.pasteBonjourService.refreshTarget("test-app-1", hostInfoList) }
        }

    @Test
    fun callbackEvent_onCompleteCalledEvenOnException() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createSyncRuntimeInfo()

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.telnetHelper.switchHost(any(), any(), any()) } throws RuntimeException("test error")
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(any()) } returns "test-app-1"

            var onCompleteCalled = false
            val callback =
                ResolveCallback(
                    updateVersionRelation = {},
                    onComplete = { onCompleteCalled = true },
                )

            resolver.emitEvent(SyncEvent.ResolveDisconnected(syncRuntimeInfo, callback))

            assertTrue(onCompleteCalled)
        }

    @Test
    fun callbackEvent_updatesVersionRelation() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createSyncRuntimeInfo()
            val hostInfo = HostInfo(24, "192.168.1.100")

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.telnetHelper.switchHost(any(), any(), any()) } returns
                Pair(hostInfo, VersionRelation.EQUAL_TO)
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(any()) } returns "test-app-1"

            var capturedRelation: VersionRelation? = null
            val callback =
                ResolveCallback(
                    updateVersionRelation = { capturedRelation = it },
                    onComplete = {},
                )

            resolver.emitEvent(SyncEvent.ResolveDisconnected(syncRuntimeInfo, callback))

            assertEquals(VersionRelation.EQUAL_TO, capturedRelation)
        }

    private fun createTestCallback(): ResolveCallback =
        ResolveCallback(
            updateVersionRelation = {},
            onComplete = {},
        )
}
