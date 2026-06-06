package com.crosspaste.sync

import com.crosspaste.app.AppInfo
import com.crosspaste.app.RatingPromptManager
import com.crosspaste.db.sync.HostInfo
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.db.sync.SyncState
import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.NetworkInterfaceInfo
import com.crosspaste.net.NetworkInterfaceService
import com.crosspaste.net.PasteBonjourService
import com.crosspaste.net.SyncInfoFactory
import com.crosspaste.net.TelnetHelper
import com.crosspaste.net.TelnetResult
import com.crosspaste.net.VersionRelation
import com.crosspaste.net.clientapi.ConnectionRefused
import com.crosspaste.net.clientapi.DecryptFail
import com.crosspaste.net.clientapi.EncryptFail
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.net.clientapi.UnknownError
import com.crosspaste.net.ws.WsClientConnector
import com.crosspaste.net.ws.WsSessionManager
import com.crosspaste.platform.Platform
import com.crosspaste.secure.SecureStore
import com.crosspaste.sync.SyncTestFixtures.createConnectedSyncRuntimeInfo
import com.crosspaste.sync.SyncTestFixtures.createConnectingSyncRuntimeInfo
import com.crosspaste.sync.SyncTestFixtures.createSyncRuntimeInfo
import com.crosspaste.sync.SyncTestFixtures.createUnverifiedSyncRuntimeInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SyncResolverTest {

    private class TestDeps {
        val pasteBonjourService: PasteBonjourService = mockk(relaxed = true)
        val nearbyDeviceManager: NearbyDeviceManager =
            mockk(relaxed = true) {
                every { nearbySyncInfos } returns MutableStateFlow(emptyList())
            }
        val networkInterfaceService: NetworkInterfaceService =
            mockk(relaxed = true) {
                // Default to "online": discoverAndConnect now gates on having a usable
                // local interface, so the resolver must see at least one by default.
                // Tests covering the offline path override this to emptyList().
                every { getCurrentUseNetworkInterfaces() } returns
                    listOf(NetworkInterfaceInfo("en0", 24, "192.168.1.2"))
            }
        val ratingPromptManager: RatingPromptManager = mockk(relaxed = true)
        val secureKeyPairSerializer: com.crosspaste.secure.SecureKeyPairSerializer = mockk(relaxed = true)
        val secureStore: SecureStore = mockk(relaxed = true)
        val syncClientApi: SyncClientApi = mockk(relaxed = true)
        val syncDeviceManager: SyncDeviceManager = mockk(relaxed = true)
        val syncInfoFactory: SyncInfoFactory = mockk(relaxed = true)
        val syncRuntimeInfoDao: SyncRuntimeInfoDao = mockk(relaxed = true)
        val telnetHelper: TelnetHelper = mockk(relaxed = true)
        val tokenCache: FakeTokenCache = FakeTokenCache()
        val appInfo: AppInfo =
            mockk(relaxed = true) {
                every { appInstanceId } returns "local-test-id"
            }
        val localPlatform: Platform =
            mockk(relaxed = true) {
                every { isDesktop() } returns true
            }
        val wsClientConnector: WsClientConnector = mockk(relaxed = true)
        val wsSessionManager: WsSessionManager = mockk(relaxed = true)

        fun stubDbRead(syncRuntimeInfo: SyncRuntimeInfo) {
            coEvery { syncRuntimeInfoDao.getSyncRuntimeInfo(syncRuntimeInfo.appInstanceId) } returns syncRuntimeInfo
        }

        fun createResolver(): SyncResolver =
            SyncResolver(
                appInfo = appInfo,
                localPlatform = localPlatform,
                lazyNearbyDeviceManager = lazy { nearbyDeviceManager },
                lazyPasteBonjourService = lazy { pasteBonjourService },
                networkInterfaceService = networkInterfaceService,
                ratingPromptManager = ratingPromptManager,
                secureKeyPairSerializer = secureKeyPairSerializer,
                secureStore = secureStore,
                syncClientApi = syncClientApi,
                syncDeviceManager = syncDeviceManager,
                syncInfoFactory = syncInfoFactory,
                syncRuntimeInfoDao = syncRuntimeInfoDao,
                telnetHelper = telnetHelper,
                tokenCache = tokenCache,
                wsClientConnector = wsClientConnector,
                wsSessionManager = wsSessionManager,
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
    fun resolve_disconnected_switchHostEqualAndHeartbeatOk_setsConnected() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo =
                createSyncRuntimeInfo(
                    hostInfoList = listOf(HostInfo(24, "192.168.1.100")),
                )
            val hostInfo = HostInfo(24, "192.168.1.100")

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.telnetHelper.switchHost(any(), any(), any(), any()) } returns
                Pair(hostInfo, TelnetResult(VersionRelation.EQUAL_TO, null))
            coEvery { deps.secureStore.existCryptPublicKey(any()) } returns true
            coEvery { deps.syncClientApi.heartbeat(any(), any(), any()) } returns
                SuccessResult(VersionRelation.EQUAL_TO)

            val capturedInfos = mutableListOf<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfos)) } returns "test-app-1"

            val callback = createTestCallback()
            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, callback))

            assertEquals(SyncState.CONNECTING, capturedInfos[0].connectState)
            assertEquals("192.168.1.100", capturedInfos[0].connectHostAddress)
            assertEquals(SyncState.CONNECTED, capturedInfos[1].connectState)
        }

    @Test
    fun resolveDisconnected_fastProbeIdentityMismatch_fallsThroughToCorrectHost() =
        runTest {
            // #4499 Phase A: a ghost squats on our last address (.108) advertising a
            // different identity. The fast probe must reject it and fall through to the
            // full-list probe, which finds the real peer that moved to .109.
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo =
                createSyncRuntimeInfo(
                    connectState = SyncState.DISCONNECTED,
                    connectHostAddress = "192.168.1.108",
                    hostInfoList =
                        listOf(
                            HostInfo(24, "192.168.1.108"),
                            HostInfo(24, "192.168.1.109"),
                        ),
                )

            deps.stubDbRead(syncRuntimeInfo)
            // Fast probe of .108 is reachable but returns a foreign identity.
            coEvery { deps.telnetHelper.telnet(any(), any(), any()) } returns
                TelnetResult(VersionRelation.EQUAL_TO, "ghost-app-id")
            // Full-list probe surfaces the real peer (identity-matched) at .109.
            coEvery { deps.telnetHelper.switchHost(any(), any(), any(), any()) } returns
                Pair(
                    HostInfo(24, "192.168.1.109"),
                    TelnetResult(VersionRelation.EQUAL_TO, syncRuntimeInfo.appInstanceId),
                )
            coEvery { deps.secureStore.existCryptPublicKey(any()) } returns true
            coEvery { deps.syncClientApi.heartbeat(any(), any(), any()) } returns
                SuccessResult(VersionRelation.EQUAL_TO)

            val capturedInfos = mutableListOf<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfos)) } returns "test-app-1"

            val callback = createTestCallback()
            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, callback))

            // Never connected to the ghost .108; converged on the real .109.
            assertEquals(SyncState.CONNECTING, capturedInfos[0].connectState)
            assertEquals("192.168.1.109", capturedInfos[0].connectHostAddress)
            assertEquals(SyncState.CONNECTED, capturedInfos[1].connectState)
            assertEquals("192.168.1.109", capturedInfos[1].connectHostAddress)
            coVerify { deps.telnetHelper.switchHost(any(), any(), any(), any()) }
        }

    @Test
    fun resolveDisconnected_switchHostReturnsLowerThan_setsIncompatible() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createSyncRuntimeInfo()
            val hostInfo = HostInfo(24, "192.168.1.100")

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.telnetHelper.switchHost(any(), any(), any(), any()) } returns
                Pair(hostInfo, TelnetResult(VersionRelation.LOWER_THAN, null))

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            val callback = createTestCallback()
            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, callback))

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
            coEvery { deps.telnetHelper.switchHost(any(), any(), any(), any()) } returns
                Pair(hostInfo, TelnetResult(VersionRelation.HIGHER_THAN, null))

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            val callback = createTestCallback()
            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, callback))

            assertEquals(SyncState.INCOMPATIBLE, capturedInfo.captured.connectState)
        }

    @Test
    fun resolveDisconnected_switchHostReturnsNull_alreadyDisconnected_doesNotRewriteDb() =
        runTest {
            // Convergence guard: a device that is already DISCONNECTED and still has no
            // reachable host must NOT be re-persisted. Re-writing DISCONNECTED on every
            // poll bumps modifyTime, re-emits the DB flow, and churns the handler.
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createSyncRuntimeInfo(connectState = SyncState.DISCONNECTED)

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.telnetHelper.switchHost(any(), any(), any(), any()) } returns null

            val callback = createTestCallback()
            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, callback))

            coVerify(exactly = 0) { deps.syncRuntimeInfoDao.updateConnectInfo(any()) }
        }

    @Test
    fun resolveDisconnected_switchHostReturnsNull_fromNonDisconnected_persistsDisconnected() =
        runTest {
            // When the device was NOT already DISCONNECTED, losing the host must still
            // persist the DISCONNECTED transition exactly once.
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createSyncRuntimeInfo(connectState = SyncState.INCOMPATIBLE)

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.telnetHelper.switchHost(any(), any(), any(), any()) } returns null

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            val callback = createTestCallback()
            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, callback))

            assertEquals(SyncState.DISCONNECTED, capturedInfo.captured.connectState)
        }

    @Test
    fun resolveDisconnected_emptyHostInfoList_switchHostCalledWithEmptyList() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createSyncRuntimeInfo(hostInfoList = emptyList())

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.telnetHelper.switchHost(any(), any(), any(), any()) } returns null

            val callback = createTestCallback()
            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, callback))

            coVerify { deps.telnetHelper.switchHost(emptyList(), any(), any(), any()) }
            // Already DISCONNECTED with no host → no redundant DB write.
            coVerify(exactly = 0) { deps.syncRuntimeInfoDao.updateConnectInfo(any()) }
        }

    @Test
    fun discoverAndConnect_noLocalInterface_skipsProbingAndStaysDisconnected() =
        runTest {
            // #4509 phase 2: with no usable local interface, every telnet would fail and
            // spam "no reachable host". The gate must skip discovery entirely — no telnet,
            // no switchHost — and not re-persist an already-DISCONNECTED device.
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo =
                createSyncRuntimeInfo(
                    connectState = SyncState.DISCONNECTED,
                    hostInfoList = listOf(HostInfo(24, "192.168.1.100")),
                )

            deps.stubDbRead(syncRuntimeInfo)
            every { deps.networkInterfaceService.getCurrentUseNetworkInterfaces() } returns emptyList()

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, createTestCallback()))

            coVerify(exactly = 0) { deps.telnetHelper.telnet(any(), any(), any()) }
            coVerify(exactly = 0) { deps.telnetHelper.switchHost(any(), any(), any(), any()) }
            coVerify(exactly = 0) { deps.syncRuntimeInfoDao.updateConnectInfo(any()) }
        }

    @Test
    fun discoverAndConnect_noLocalInterface_fromNonDisconnected_persistsDisconnectedOnce() =
        runTest {
            // A device that was CONNECTED loses all local interfaces: the gate must still
            // record the DISCONNECTED transition exactly once (so the UI reflects reality)
            // without probing any host.
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo =
                createConnectedSyncRuntimeInfo(hostAddress = "192.168.1.108")

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.wsSessionManager.isConnected(any()) } returns false
            // Heartbeat on the current address fails → falls through to discoverAndConnect.
            coEvery { deps.syncClientApi.heartbeat(any(), any(), any()) } returns ConnectionRefused
            every { deps.networkInterfaceService.getCurrentUseNetworkInterfaces() } returns emptyList()

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, createTestCallback()))

            assertEquals(SyncState.DISCONNECTED, capturedInfo.captured.connectState)
            coVerify(exactly = 0) { deps.telnetHelper.switchHost(any(), any(), any(), any()) }
        }

    // ========== A2. Phase E: recency-first probing + active switch ==========

    @Test
    fun discoverReachableHost_fastProbesFreshestAddressFirst() =
        runTest {
            // Two known addresses; .109 was advertised more recently than .108.
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo =
                createSyncRuntimeInfo(
                    connectState = SyncState.DISCONNECTED,
                    hostInfoList =
                        listOf(
                            HostInfo(24, "192.168.1.108", lastSeen = 1L),
                            HostInfo(24, "192.168.1.109", lastSeen = 100L),
                        ),
                )

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.telnetHelper.telnet(any(), any(), any()) } returns
                TelnetResult(VersionRelation.EQUAL_TO, syncRuntimeInfo.appInstanceId)
            coEvery { deps.secureStore.existCryptPublicKey(any()) } returns true
            coEvery { deps.syncClientApi.heartbeat(any(), any(), any()) } returns
                SuccessResult(VersionRelation.EQUAL_TO)

            val capturedInfos = mutableListOf<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfos)) } returns "test-app-1"

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, createTestCallback()))

            // Fresh .109 is the fast-probe target and the address we connect on.
            coVerify { deps.telnetHelper.telnet("192.168.1.109", any(), any()) }
            assertEquals(SyncState.CONNECTING, capturedInfos[0].connectState)
            assertEquals("192.168.1.109", capturedInfos[0].connectHostAddress)
        }

    @Test
    fun verifyConnection_currentAddressDead_activeSwitchesWithoutDisconnect() =
        runTest {
            // CONNECTED on .108; heartbeat there fails, but the peer is reachable at the
            // freshly-advertised .109. Must switch CONNECTED -> CONNECTING -> CONNECTED
            // with NO intervening DISCONNECTED write (#4499 weakness ①).
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo =
                createSyncRuntimeInfo(
                    connectState = SyncState.CONNECTED,
                    connectHostAddress = "192.168.1.108",
                    connectNetworkPrefixLength = 24,
                    hostInfoList =
                        listOf(
                            HostInfo(24, "192.168.1.108", lastSeen = 1L),
                            HostInfo(24, "192.168.1.109", lastSeen = 100L),
                        ),
                )

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.wsSessionManager.isConnected(any()) } returns false
            coEvery { deps.secureStore.existCryptPublicKey(any()) } returns true
            coEvery { deps.telnetHelper.telnet(any(), any(), any()) } returns
                TelnetResult(VersionRelation.EQUAL_TO, syncRuntimeInfo.appInstanceId)
            // First heartbeat (verify .108) fails; second (authenticate .109) succeeds.
            coEvery { deps.syncClientApi.heartbeat(any(), any(), any()) } returnsMany
                listOf(ConnectionRefused, SuccessResult(VersionRelation.EQUAL_TO))

            val capturedInfos = mutableListOf<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfos)) } returns "test-app-1"

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, createTestCallback()))

            assertTrue(capturedInfos.none { it.connectState == SyncState.DISCONNECTED })
            assertEquals(SyncState.CONNECTING, capturedInfos[0].connectState)
            assertEquals("192.168.1.109", capturedInfos[0].connectHostAddress)
            assertEquals(SyncState.CONNECTED, capturedInfos.last().connectState)
            assertEquals("192.168.1.109", capturedInfos.last().connectHostAddress)
        }

    @Test
    fun verifyConnection_connectAddressNotInHostInfoList_butAlive_staysConnected() =
        runTest {
            // Intentional Phase E boundary: connectHostAddress may have been LRU-evicted
            // from hostInfoList (the peer broadcast newer addresses) yet still be alive.
            // verifyConnection must heartbeat it DIRECTLY and keep the connection — it must
            // NOT require connectHostAddress to be a member of hostInfoList. This guards
            // against a future refactor that only ever probes hostInfoList.
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo =
                createSyncRuntimeInfo(
                    connectState = SyncState.CONNECTED,
                    connectHostAddress = "192.168.1.5",
                    connectNetworkPrefixLength = 24,
                    // .5 is deliberately absent from the host list.
                    hostInfoList =
                        (1..HostInfo.MAX_RECENT_HOST_INFO).map { i ->
                            HostInfo(24, "192.168.9.$i", lastSeen = i.toLong())
                        },
                )

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.wsSessionManager.isConnected(any()) } returns false
            coEvery { deps.syncClientApi.heartbeat(any(), any(), any()) } returns
                SuccessResult(VersionRelation.EQUAL_TO)

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, createTestCallback()))

            // Heartbeat on the (off-list) connect address succeeded → still CONNECTED,
            // no state write at all.
            coVerify(exactly = 0) { deps.syncRuntimeInfoDao.updateConnectInfo(any()) }
        }

    @Test
    fun verifyConnection_currentAddressDeadAndNoReachableHost_setsDisconnected() =
        runTest {
            // CONNECTED, heartbeat fails, and re-discovery finds nothing reachable ->
            // the active-switch path falls through to DISCONNECTED.
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo(hostAddress = "192.168.1.108")

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.wsSessionManager.isConnected(any()) } returns false
            coEvery { deps.syncClientApi.heartbeat(any(), any(), any()) } returns ConnectionRefused
            coEvery { deps.telnetHelper.telnet(any(), any(), any()) } returns null
            coEvery { deps.telnetHelper.switchHost(any(), any(), any(), any()) } returns null

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, createTestCallback()))

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
            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, callback))

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
            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, callback))

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
            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, callback))

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
            coEvery { deps.telnetHelper.telnet(any(), any(), any()) } returns
                TelnetResult(VersionRelation.EQUAL_TO, null)

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            val callback = createTestCallback()
            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, callback))

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
            coEvery { deps.telnetHelper.switchHost(any(), any(), any(), any()) } returns null

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            val callback = createTestCallback()
            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, callback))

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
            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, callback))

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

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, createTestCallback()))

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

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, createTestCallback()))

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

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, createTestCallback()))

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

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, createTestCallback()))

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

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, createTestCallback()))

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

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, createTestCallback()))

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

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, createTestCallback()))

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

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, createTestCallback()))

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

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, createTestCallback()))

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

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, createTestCallback()))

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
            coEvery { deps.telnetHelper.telnet(any(), any(), any()) } returns
                TelnetResult(VersionRelation.EQUAL_TO, null)

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, createTestCallback()))

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
            coEvery { deps.telnetHelper.telnet(any(), any(), any()) } returns
                TelnetResult(VersionRelation.EQUAL_TO, null)

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, createTestCallback()))

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
            coEvery { deps.telnetHelper.telnet(any(), any(), any()) } returns
                TelnetResult(VersionRelation.LOWER_THAN, null)

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, createTestCallback()))

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

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, createTestCallback()))

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
            coEvery { deps.telnetHelper.switchHost(any(), any(), any(), any()) } returns null

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, createTestCallback()))

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

    // ========== F. Event dispatch and callback ==========

    @Test
    fun resolve_disconnected_callsSwitchHost() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createSyncRuntimeInfo(connectState = SyncState.DISCONNECTED)

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.telnetHelper.switchHost(any(), any(), any(), any()) } returns null

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            val callback = createTestCallback()
            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, callback))

            coVerify { deps.telnetHelper.switchHost(any(), any(), any(), any()) }
        }

    @Test
    fun resolve_incompatible_callsSwitchHost() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createSyncRuntimeInfo(connectState = SyncState.INCOMPATIBLE)

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.telnetHelper.switchHost(any(), any(), any(), any()) } returns null

            val capturedInfo = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(capturedInfo)) } returns "test-app-1"

            val callback = createTestCallback()
            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, callback))

            coVerify { deps.telnetHelper.switchHost(any(), any(), any(), any()) }
        }

    @Test
    fun resolve_connecting_callsAuthenticateNotSwitchHost() =
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
            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, callback))

            // resolveConnecting path is taken, which calls telnet (not switchHost)
            coVerify(exactly = 0) { deps.telnetHelper.switchHost(any(), any(), any(), any()) }
        }

    @Test
    fun forceResolve_callsRefreshThenResolve() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createSyncRuntimeInfo(connectState = SyncState.DISCONNECTED)

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.telnetHelper.switchHost(any(), any(), any(), any()) } returns null
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(any()) } returns "test-app-1"

            val callback = createTestCallback()
            resolver.emitEvent(SyncEvent.ForceResolve(syncRuntimeInfo, callback))

            verify { deps.pasteBonjourService.refreshTarget(any(), any()) }
            coVerify { deps.telnetHelper.switchHost(any(), any(), any(), any()) }
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
            coEvery { deps.telnetHelper.switchHost(any(), any(), any(), any()) } throws RuntimeException("test error")
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(any()) } returns "test-app-1"

            var onCompleteCalled = false
            val callback =
                ResolveCallback(
                    updateVersionRelation = {},
                    onComplete = { onCompleteCalled = true },
                )

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, callback))

            assertTrue(onCompleteCalled)
        }

    @Test
    fun processEvent_cancellationException_propagatesNotSwallowed() =
        runTest {
            // Regression guard for the resolver infinite-loop: a CancellationException
            // raised while resolving (e.g. scope teardown / shutdown) MUST propagate out
            // of processEvent instead of being caught-and-logged. Swallowing it broke
            // structured-concurrency cancellation and let buffered Resolve events
            // re-process endlessly ("Failed to process event" flood).
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createSyncRuntimeInfo(connectState = SyncState.DISCONNECTED)

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.telnetHelper.switchHost(any(), any(), any(), any()) } throws
                CancellationException("scope cancelled")

            assertFailsWith<CancellationException> {
                resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, createTestCallback()))
            }
        }

    @Test
    fun processEvent_nonCancellationException_swallowedAndLogged() =
        runTest {
            // Counterpart to the cancellation test: ordinary failures must still be
            // caught (not propagated), so one bad device cannot tear down the pipeline.
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createSyncRuntimeInfo(connectState = SyncState.DISCONNECTED)

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.telnetHelper.switchHost(any(), any(), any(), any()) } throws
                RuntimeException("boom")

            // Should NOT throw — the runtime exception is swallowed.
            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, createTestCallback()))
        }

    @Test
    fun callbackEvent_updatesVersionRelation() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createSyncRuntimeInfo()
            val hostInfo = HostInfo(24, "192.168.1.100")

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.telnetHelper.switchHost(any(), any(), any(), any()) } returns
                Pair(hostInfo, TelnetResult(VersionRelation.EQUAL_TO, null))
            coEvery { deps.secureStore.existCryptPublicKey(any()) } returns true
            coEvery { deps.syncClientApi.heartbeat(any(), any(), any()) } returns
                SuccessResult(VersionRelation.EQUAL_TO)
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(any()) } returns "test-app-1"

            var capturedRelation: VersionRelation? = null
            val callback =
                ResolveCallback(
                    updateVersionRelation = { capturedRelation = it },
                    onComplete = {},
                )

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, callback))

            assertEquals(VersionRelation.EQUAL_TO, capturedRelation)
        }

    // ========== F. resolveExtension ==========

    @Test
    fun resolveExtension_connected_probeSucceeds_staysConnected() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createExtensionSyncRuntimeInfo(connectState = SyncState.CONNECTED)

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.wsSessionManager.probe(syncRuntimeInfo.appInstanceId) } returns true

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, createTestCallback()))

            coVerify(exactly = 0) { deps.syncRuntimeInfoDao.updateConnectInfo(any()) }
        }

    @Test
    fun resolveExtension_connected_probeFails_setsDisconnected() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createExtensionSyncRuntimeInfo(connectState = SyncState.CONNECTED)

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.wsSessionManager.probe(syncRuntimeInfo.appInstanceId) } returns false

            val captured = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(captured)) } returns
                syncRuntimeInfo.appInstanceId

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, createTestCallback()))

            assertEquals(SyncState.DISCONNECTED, captured.captured.connectState)
        }

    @Test
    fun resolveExtension_disconnected_wsConnected_setsConnected() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createExtensionSyncRuntimeInfo(connectState = SyncState.DISCONNECTED)

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.wsSessionManager.probe(syncRuntimeInfo.appInstanceId) } returns true

            val captured = slot<SyncRuntimeInfo>()
            coEvery { deps.syncRuntimeInfoDao.updateConnectInfo(capture(captured)) } returns
                syncRuntimeInfo.appInstanceId

            var capturedRelation: VersionRelation? = null
            val callback =
                ResolveCallback(
                    updateVersionRelation = { capturedRelation = it },
                )

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, callback))

            assertEquals(SyncState.CONNECTED, captured.captured.connectState)
            assertEquals(VersionRelation.EQUAL_TO, capturedRelation)
        }

    @Test
    fun resolveExtension_disconnected_wsNotConnected_invokesMarkPollFailure() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createExtensionSyncRuntimeInfo(connectState = SyncState.DISCONNECTED)

            deps.stubDbRead(syncRuntimeInfo)
            coEvery { deps.wsSessionManager.probe(syncRuntimeInfo.appInstanceId) } returns false

            var failureCalls = 0
            val callback =
                ResolveCallback(
                    updateVersionRelation = {},
                    markPollFailure = { failureCalls++ },
                )

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, callback))

            assertEquals(1, failureCalls)
            coVerify(exactly = 0) { deps.syncRuntimeInfoDao.updateConnectInfo(any()) }
        }

    @Test
    fun resolveExtension_unexpectedState_noOp() =
        runTest {
            val deps = TestDeps()
            val resolver = deps.createResolver()
            val syncRuntimeInfo = createExtensionSyncRuntimeInfo(connectState = SyncState.UNVERIFIED)

            deps.stubDbRead(syncRuntimeInfo)

            var failureCalls = 0
            val callback =
                ResolveCallback(
                    updateVersionRelation = {},
                    markPollFailure = { failureCalls++ },
                )

            resolver.emitEvent(SyncEvent.Resolve(syncRuntimeInfo, callback))

            assertEquals(0, failureCalls)
            coVerify(exactly = 0) { deps.syncRuntimeInfoDao.updateConnectInfo(any()) }
            coVerify(exactly = 0) { deps.wsSessionManager.probe(any()) }
        }

    private fun extensionPlatform(): Platform =
        Platform(
            name = Platform.CHROME_EXTENSION,
            arch = "any",
            bitMode = 64,
            version = "1.0.0",
        )

    private fun createExtensionSyncRuntimeInfo(
        appInstanceId: String = "chrome-ext-1",
        connectState: Int = SyncState.CONNECTED,
    ): SyncRuntimeInfo =
        createSyncRuntimeInfo(
            appInstanceId = appInstanceId,
            platform = extensionPlatform(),
            connectState = connectState,
        )

    private fun createTestCallback(): ResolveCallback =
        ResolveCallback(
            updateVersionRelation = {},
            onComplete = {},
        )
}
