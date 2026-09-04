package com.crosspaste.cli

import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppTokenApi
import com.crosspaste.db.sync.HostInfo
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncState
import com.crosspaste.dto.sync.EndpointInfo
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.net.PasteBonjourService
import com.crosspaste.pairing.v3.PairingCapabilityFlag
import com.crosspaste.pairing.v3.PairingSessionState
import com.crosspaste.pairing.v3.PairingSessionUiState
import com.crosspaste.pairing.v3.PakeRole
import com.crosspaste.platform.Platform
import com.crosspaste.sync.NearbyDeviceManager
import com.crosspaste.sync.PairingCredentialRefreshResult
import com.crosspaste.sync.PairingCredentialType
import com.crosspaste.sync.PendingKeyExchange
import com.crosspaste.sync.PendingKeyExchangeStore
import com.crosspaste.sync.SasCode
import com.crosspaste.sync.SyncManager
import com.crosspaste.sync.V3Pin
import com.crosspaste.ui.devices.PairingV3Recovery
import com.crosspaste.ui.devices.PairingV3UiController
import com.crosspaste.ui.devices.PairingV3UiError
import com.crosspaste.ui.devices.PairingV3UiResult
import com.crosspaste.utils.DateUtils
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CliPairingServiceTest {

    private companion object {
        const val PEER_ID = "peer-1"
        const val PEER_NAME = "Laptop"
    }

    private val platform = Platform(name = Platform.MACOS, arch = "arm64", bitMode = 64, version = "15")

    private class Fixture(
        localPairingVersion: Int = 2,
    ) {
        val nearbySyncInfos = MutableStateFlow<List<SyncInfo>>(listOf())
        val searching = MutableStateFlow(false)
        val runtimeInfos = MutableStateFlow<List<SyncRuntimeInfo>>(listOf())
        val pendingVerifiersFlow = MutableStateFlow<Set<String>>(setOf())

        val appTokenApi =
            mockk<AppTokenApi> {
                every { pendingVerifiers } returns pendingVerifiersFlow
            }
        val pendingKeyExchangeStore = PendingKeyExchangeStore()
        val nearbyDeviceManager =
            mockk<NearbyDeviceManager> {
                every { nearbySyncInfos } returns this@Fixture.nearbySyncInfos
                every { searching } returns this@Fixture.searching
            }
        val v3Sessions = MutableStateFlow<List<PairingSessionUiState>>(listOf())
        val pairingV3UiController =
            mockk<PairingV3UiController> {
                every { sessions } returns v3Sessions
            }
        val pasteBonjourService =
            mockk<PasteBonjourService> {
                every { refreshAll() } just Runs
            }
        val syncManager =
            mockk<SyncManager> {
                every { realTimeSyncRuntimeInfos } returns runtimeInfos
                every { updateSyncInfo(any()) } just Runs
                every { refresh(any(), any()) } just Runs
                every { exchangeKeysForPairing(any()) } just Runs
                every { cancelPairing(any()) } just Runs
            }

        val service =
            CliPairingService(
                appTokenApi = appTokenApi,
                nearbyDeviceManager = nearbyDeviceManager,
                pendingKeyExchangeStore = pendingKeyExchangeStore,
                pairingCapabilityFlag = PairingCapabilityFlag(localPairingVersion),
                pairingV3UiController = pairingV3UiController,
                pasteBonjourService = pasteBonjourService,
                syncManager = syncManager,
            )
    }

    private fun syncInfo(pairingVersion: Int? = 2): SyncInfo =
        SyncInfo(
            appInfo =
                AppInfo(
                    appInstanceId = PEER_ID,
                    appVersion = "2.1.7",
                    appRevision = "Unknown",
                    userName = "tester",
                    pairingVersion = pairingVersion,
                ),
            endpointInfo =
                EndpointInfo(
                    deviceId = "device-1",
                    deviceName = PEER_NAME,
                    platform = platform,
                    hostInfoList = listOf(HostInfo(24, "192.168.1.10")),
                    port = 13129,
                ),
        )

    private fun runtimeInfo(
        connectState: Int,
        connectHostAddress: String? = "192.168.1.10",
    ): SyncRuntimeInfo =
        SyncRuntimeInfo(
            appInstanceId = PEER_ID,
            appVersion = "2.1.7",
            userName = "tester",
            deviceId = "device-1",
            deviceName = PEER_NAME,
            platform = platform,
            port = 13129,
            connectHostAddress = connectHostAddress,
            connectState = connectState,
        )

    // region token

    private fun Fixture.storeExchange(
        appInstanceId: String,
        sas: Int,
        ageMillis: Long = 0L,
    ) {
        pendingKeyExchangeStore.put(
            appInstanceId,
            PendingKeyExchange(
                signPublicKey = byteArrayOf(1),
                cryptPublicKey = byteArrayOf(2),
                sas = sas,
                timestamp = DateUtils.nowEpochMilliseconds() - ageMillis,
                generation = 1L,
            ),
        )
    }

    @Test
    fun `pairingToken is empty when no exchange is pending`() {
        val fixture = Fixture()

        assertTrue(
            fixture.service
                .pairingToken()
                .requests
                .isEmpty(),
        )
    }

    @Test
    fun `pairingToken binds each requester to its own exchange code`() {
        val fixture = Fixture()
        fixture.pendingVerifiersFlow.value = setOf(PEER_ID, "peer-2")
        fixture.storeExchange(PEER_ID, sas = 42420)
        fixture.storeExchange("peer-2", sas = 987654)
        fixture.nearbySyncInfos.value = listOf(syncInfo())

        val requests =
            fixture.service
                .pairingToken()
                .requests
                .associateBy { it.appInstanceId }

        assertEquals(setOf(PEER_ID, "peer-2"), requests.keys)
        assertEquals("042420", requests.getValue(PEER_ID).token)
        assertEquals(PEER_NAME, requests.getValue(PEER_ID).deviceName)
        assertEquals("987654", requests.getValue("peer-2").token)
        assertNull(requests.getValue("peer-2").deviceName)
    }

    @Test
    fun `pairingToken excludes verifiers without a live exchange`() {
        val fixture = Fixture()
        // A legacy QR verifier holds no exchange; an abandoned one is expired
        fixture.pendingVerifiersFlow.value = setOf("qr-peer", "expired-peer", PEER_ID)
        fixture.storeExchange("expired-peer", sas = 111111, ageMillis = 120_000L)
        fixture.storeExchange(PEER_ID, sas = 222222)

        val requests = fixture.service.pairingToken().requests

        assertEquals(PEER_ID, requests.single().appInstanceId)
        assertEquals("222222", requests.single().token)
    }

    @Test
    fun `pairingToken falls back to the runtime info display name`() {
        val fixture = Fixture()
        fixture.pendingVerifiersFlow.value = setOf(PEER_ID)
        fixture.storeExchange(PEER_ID, sas = 123456)
        fixture.runtimeInfos.value = listOf(runtimeInfo(SyncState.UNVERIFIED))

        val dto = fixture.service.pairingToken()

        assertEquals(PEER_NAME, dto.requests.single().deviceName)
    }

    private fun v3Session(
        peerAppInstanceId: String = PEER_ID,
        role: PakeRole = PakeRole.ACCEPTOR,
        state: PairingSessionState = PairingSessionState.PIN_AVAILABLE,
        pin: String? = "654321",
        peerDisplayName: String = PEER_NAME,
    ): PairingSessionUiState =
        PairingSessionUiState(
            sessionId = "session-$peerAppInstanceId",
            role = role,
            peerDisplayName = peerDisplayName,
            peerAppInstanceId = peerAppInstanceId,
            peerKeyFingerprintDisplay = "abcd1234",
            pin = pin,
            pinExpiresAt = 1_000L,
            tokenGeneration = 3L,
            state = state,
            createdAt = 0L,
        )

    @Test
    fun `pairingToken includes v3 acceptor sessions showing a PIN`() {
        val fixture = Fixture()
        fixture.v3Sessions.value = listOf(v3Session())

        val request =
            fixture.service
                .pairingToken()
                .requests
                .single()

        assertEquals(PEER_ID, request.appInstanceId)
        assertEquals(PEER_NAME, request.deviceName)
        assertEquals("654321", request.token)
        assertEquals(CliPairCredential.V3_PIN, request.credentialType)
        assertEquals(1_000L, request.pinExpiresAt)
        assertEquals(3L, request.tokenGeneration)
    }

    @Test
    fun `pairingToken merges v2 exchanges with v3 sessions`() {
        val fixture = Fixture()
        fixture.pendingVerifiersFlow.value = setOf("v2-peer")
        fixture.storeExchange("v2-peer", sas = 42420)
        fixture.v3Sessions.value = listOf(v3Session())

        val requests =
            fixture.service
                .pairingToken()
                .requests
                .associateBy { it.appInstanceId }

        assertEquals(setOf("v2-peer", PEER_ID), requests.keys)
        assertEquals(CliPairCredential.V2_SAS, requests.getValue("v2-peer").credentialType)
        assertEquals(CliPairCredential.V3_PIN, requests.getValue(PEER_ID).credentialType)
    }

    @Test
    fun `pairingToken excludes v3 sessions without a visible PIN`() {
        val fixture = Fixture()
        fixture.v3Sessions.value =
            listOf(
                v3Session(peerAppInstanceId = "initiator-role", role = PakeRole.INITIATOR),
                v3Session(peerAppInstanceId = "negotiating", state = PairingSessionState.PAKE_NEGOTIATING),
                v3Session(peerAppInstanceId = "no-pin", pin = null),
            )

        assertTrue(
            fixture.service
                .pairingToken()
                .requests
                .isEmpty(),
        )
    }

    @Test
    fun `pairingToken falls back to the runtime display name for a blank v3 peer name`() {
        val fixture = Fixture()
        fixture.runtimeInfos.value = listOf(runtimeInfo(SyncState.UNVERIFIED))
        fixture.v3Sessions.value = listOf(v3Session(peerDisplayName = ""))

        assertEquals(
            PEER_NAME,
            fixture.service
                .pairingToken()
                .requests
                .single()
                .deviceName,
        )
    }

    @Test
    fun `armAcceptanceWindow maps START to open and RENEW to renew`() {
        val fixture = Fixture()
        every { fixture.pairingV3UiController.openAcceptanceWindow() } just Runs
        every { fixture.pairingV3UiController.renewAcceptanceWindow() } just Runs

        fixture.service.armAcceptanceWindow(CliTokenArm.START)
        fixture.service.armAcceptanceWindow(CliTokenArm.RENEW)

        verify(exactly = 1) { fixture.pairingV3UiController.openAcceptanceWindow() }
        verify(exactly = 1) { fixture.pairingV3UiController.renewAcceptanceWindow() }
    }

    // endregion

    // region nearby

    @Test
    fun `nearby maps sync infos with the negotiated credential type`() =
        runTest {
            val fixture = Fixture(localPairingVersion = 2)
            fixture.nearbySyncInfos.value =
                listOf(
                    syncInfo(pairingVersion = 3),
                    syncInfo(pairingVersion = null).let {
                        it.copy(appInfo = it.appInfo.copy(appInstanceId = "legacy-peer"))
                    },
                )

            val devices = fixture.service.nearbyDevices(refresh = false)

            assertEquals(2, devices.size)
            // Local advertises v2, so a v3-capable peer still negotiates SAS
            assertEquals("SAS_CODE", devices[0].credentialType)
            assertEquals("QR_BEARER_TOKEN", devices[1].credentialType)
            verify(exactly = 0) { fixture.pasteBonjourService.refreshAll() }
        }

    @Test
    fun `nearby refresh triggers a scan and waits for it to finish`() =
        runTest {
            val fixture = Fixture()
            every { fixture.pasteBonjourService.refreshAll() } answers {
                fixture.searching.value = true
            }
            // Simulates the scan completing while the service waits on it
            val scanFinisher =
                launch {
                    fixture.searching.first { it }
                    fixture.nearbySyncInfos.value = listOf(syncInfo())
                    fixture.searching.value = false
                }

            val devices = fixture.service.nearbyDevices(refresh = true)

            assertEquals(1, devices.size)
            verify(exactly = 1) { fixture.pasteBonjourService.refreshAll() }
            scanFinisher.join()
        }

    // endregion

    // region initiate

    @Test
    fun `initiate refuses an unknown device`() =
        runTest {
            val fixture = Fixture()

            val outcome = fixture.service.initiate("nobody")

            assertIs<CliPairingService.InitiateOutcome.DeviceNotFound>(outcome)
        }

    @Test
    fun `initiate refuses an already paired device`() =
        runTest {
            val fixture = Fixture()
            fixture.runtimeInfos.value = listOf(runtimeInfo(SyncState.CONNECTED))

            val outcome = fixture.service.initiate(PEER_ID)

            assertIs<CliPairingService.InitiateOutcome.AlreadyPaired>(outcome)
        }

    @Test
    fun `initiate v2 persists the device warms up the exchange and starts a session`() =
        runTest {
            val fixture = Fixture()
            fixture.nearbySyncInfos.value = listOf(syncInfo(pairingVersion = 2))
            every { fixture.syncManager.updateSyncInfo(any()) } answers {
                fixture.runtimeInfos.value = listOf(runtimeInfo(SyncState.UNVERIFIED))
            }
            coEvery { fixture.syncManager.refreshPairingCredentialType(PEER_ID) } returns
                PairingCredentialRefreshResult.Resolved(PairingCredentialType.SAS_CODE)

            val outcome = fixture.service.initiate(PEER_ID)

            val started = assertIs<CliPairingService.InitiateOutcome.Started>(outcome)
            assertEquals("SAS_CODE", started.session.credentialType)
            assertEquals(PEER_NAME, started.session.deviceName)
            assertNull(started.session.peerFingerprint)
            verify(exactly = 1) { fixture.syncManager.updateSyncInfo(any()) }
            verify(exactly = 1) { fixture.syncManager.exchangeKeysForPairing(PEER_ID) }
        }

    @Test
    fun `initiate refuses a qr-only peer`() =
        runTest {
            val fixture = Fixture()
            fixture.nearbySyncInfos.value = listOf(syncInfo(pairingVersion = null))
            every { fixture.syncManager.updateSyncInfo(any()) } answers {
                fixture.runtimeInfos.value = listOf(runtimeInfo(SyncState.UNVERIFIED))
            }
            coEvery { fixture.syncManager.refreshPairingCredentialType(PEER_ID) } returns
                PairingCredentialRefreshResult.Resolved(PairingCredentialType.QR_BEARER_TOKEN)

            val outcome = fixture.service.initiate(PEER_ID)

            assertIs<CliPairingService.InitiateOutcome.UnsupportedPeer>(outcome)
        }

    @Test
    fun `initiate reports an unreachable device after the resolve timeout`() =
        runTest {
            val fixture = Fixture()
            fixture.nearbySyncInfos.value = listOf(syncInfo())
            // updateSyncInfo never produces a resolvable runtime row

            val outcome = fixture.service.initiate(PEER_ID)

            val unavailable = assertIs<CliPairingService.InitiateOutcome.Unavailable>(outcome)
            assertContains(unavailable.message, "reachable")
        }

    @Test
    fun `initiate keeps waiting through a transient disconnected probe`() =
        runTest {
            val fixture = Fixture()
            fixture.nearbySyncInfos.value = listOf(syncInfo())
            every { fixture.syncManager.updateSyncInfo(any()) } answers {
                fixture.runtimeInfos.value = listOf(runtimeInfo(SyncState.DISCONNECTED, connectHostAddress = null))
            }
            coEvery { fixture.syncManager.refreshPairingCredentialType(PEER_ID) } returns
                PairingCredentialRefreshResult.Resolved(PairingCredentialType.SAS_CODE)

            // Flip to UNVERIFIED while the service is waiting
            val outcomeDeferred =
                async {
                    fixture.service.initiate(PEER_ID)
                }
            yield()
            fixture.runtimeInfos.value = listOf(runtimeInfo(SyncState.UNVERIFIED))
            val outcome = outcomeDeferred.await()

            assertIs<CliPairingService.InitiateOutcome.Started>(outcome)
        }

    @Test
    fun `initiate v3 returns fingerprint and pin expiry from the controller`() =
        runTest {
            val fixture = Fixture(localPairingVersion = 3)
            fixture.nearbySyncInfos.value = listOf(syncInfo(pairingVersion = 3))
            every { fixture.syncManager.updateSyncInfo(any()) } answers {
                fixture.runtimeInfos.value = listOf(runtimeInfo(SyncState.UNVERIFIED))
            }
            coEvery { fixture.syncManager.refreshPairingCredentialType(PEER_ID) } returns
                PairingCredentialRefreshResult.Resolved(PairingCredentialType.V3_PIN)
            coEvery { fixture.pairingV3UiController.startPairing(PEER_ID) } returns
                PairingV3UiResult.SessionReady(
                    sessionId = "v3-session",
                    tokenGeneration = 1L,
                    pinExpiresAt = 456L,
                    peerKeyFingerprintDisplay = "AB:CD",
                )

            val outcome = fixture.service.initiate(PEER_ID)

            val started = assertIs<CliPairingService.InitiateOutcome.Started>(outcome)
            assertEquals("V3_PIN", started.session.credentialType)
            assertEquals("AB:CD", started.session.peerFingerprint)
            assertEquals(456L, started.session.pinExpiresAt)
            verify(exactly = 0) { fixture.syncManager.exchangeKeysForPairing(any()) }
        }

    @Test
    fun `initiate v3 surfaces a closed acceptance window as unavailable`() =
        runTest {
            val fixture = Fixture(localPairingVersion = 3)
            fixture.nearbySyncInfos.value = listOf(syncInfo(pairingVersion = 3))
            every { fixture.syncManager.updateSyncInfo(any()) } answers {
                fixture.runtimeInfos.value = listOf(runtimeInfo(SyncState.UNVERIFIED))
            }
            coEvery { fixture.syncManager.refreshPairingCredentialType(PEER_ID) } returns
                PairingCredentialRefreshResult.Resolved(PairingCredentialType.V3_PIN)
            coEvery { fixture.pairingV3UiController.startPairing(PEER_ID) } returns
                PairingV3UiResult.Error(PairingV3UiError.NOT_ACCEPTING, PairingV3Recovery.RETRY_START)

            val outcome = fixture.service.initiate(PEER_ID)

            val unavailable = assertIs<CliPairingService.InitiateOutcome.Unavailable>(outcome)
            assertContains(unavailable.message, "not accepting")
        }

    @Test
    fun `re-initiating the same device cancels the previous session`() =
        runTest {
            val fixture = Fixture()
            fixture.nearbySyncInfos.value = listOf(syncInfo())
            every { fixture.syncManager.updateSyncInfo(any()) } answers {
                fixture.runtimeInfos.value = listOf(runtimeInfo(SyncState.UNVERIFIED))
            }
            coEvery { fixture.syncManager.refreshPairingCredentialType(PEER_ID) } returns
                PairingCredentialRefreshResult.Resolved(PairingCredentialType.SAS_CODE)

            val first = assertIs<CliPairingService.InitiateOutcome.Started>(fixture.service.initiate(PEER_ID))
            assertIs<CliPairingService.InitiateOutcome.Started>(fixture.service.initiate(PEER_ID))

            verify(exactly = 1) { fixture.syncManager.cancelPairing(PEER_ID) }
            assertIs<CliPairingService.SubmitOutcome.SessionNotFound>(
                fixture.service.submit(first.session.sessionId, "123456"),
            )
        }

    @Test
    fun `concurrent initiates for the same device are serialized`() =
        runTest {
            val fixture = Fixture()
            fixture.nearbySyncInfos.value = listOf(syncInfo())
            every { fixture.syncManager.updateSyncInfo(any()) } answers {
                fixture.runtimeInfos.value = listOf(runtimeInfo(SyncState.UNVERIFIED))
            }
            val enteredCredentialRefresh = CompletableDeferred<Unit>()
            val releaseCredentialRefresh = CompletableDeferred<Unit>()
            coEvery { fixture.syncManager.refreshPairingCredentialType(PEER_ID) } coAnswers {
                enteredCredentialRefresh.complete(Unit)
                releaseCredentialRefresh.await()
                PairingCredentialRefreshResult.Resolved(PairingCredentialType.SAS_CODE)
            }

            val first = async { fixture.service.initiate(PEER_ID) }
            enteredCredentialRefresh.await()
            val second = async { fixture.service.initiate(PEER_ID) }
            yield()
            assertFalse(second.isCompleted)

            releaseCredentialRefresh.complete(Unit)
            val firstSession = assertIs<CliPairingService.InitiateOutcome.Started>(first.await()).session
            assertIs<CliPairingService.InitiateOutcome.Started>(second.await())
            verify(exactly = 1) { fixture.syncManager.cancelPairing(PEER_ID) }
            assertIs<CliPairingService.SubmitOutcome.SessionNotFound>(
                fixture.service.submit(firstSession.sessionId, "123456"),
            )
        }

    // endregion

    // region submit

    private suspend fun startedSasSession(fixture: Fixture): CliPairSessionDto {
        fixture.nearbySyncInfos.value = listOf(syncInfo())
        every { fixture.syncManager.updateSyncInfo(any()) } answers {
            fixture.runtimeInfos.value = listOf(runtimeInfo(SyncState.UNVERIFIED))
        }
        coEvery { fixture.syncManager.refreshPairingCredentialType(PEER_ID) } returns
            PairingCredentialRefreshResult.Resolved(PairingCredentialType.SAS_CODE)
        return assertIs<CliPairingService.InitiateOutcome.Started>(fixture.service.initiate(PEER_ID)).session
    }

    private suspend fun startedV3Session(fixture: Fixture): CliPairSessionDto {
        fixture.nearbySyncInfos.value = listOf(syncInfo(pairingVersion = 3))
        every { fixture.syncManager.updateSyncInfo(any()) } answers {
            fixture.runtimeInfos.value = listOf(runtimeInfo(SyncState.UNVERIFIED))
        }
        coEvery { fixture.syncManager.refreshPairingCredentialType(PEER_ID) } returns
            PairingCredentialRefreshResult.Resolved(PairingCredentialType.V3_PIN)
        coEvery { fixture.pairingV3UiController.startPairing(PEER_ID) } returns
            PairingV3UiResult.SessionReady(
                sessionId = "v3-session",
                tokenGeneration = 1L,
                pinExpiresAt = 456L,
                peerKeyFingerprintDisplay = "AB:CD",
            )
        return assertIs<CliPairingService.InitiateOutcome.Started>(fixture.service.initiate(PEER_ID)).session
    }

    @Test
    fun `submit rejects unknown sessions and malformed codes`() =
        runTest {
            val fixture = Fixture()
            assertIs<CliPairingService.SubmitOutcome.SessionNotFound>(
                fixture.service.submit("nope", "123456"),
            )

            val session = startedSasSession(fixture)
            assertIs<CliPairingService.SubmitOutcome.InvalidCode>(
                fixture.service.submit(session.sessionId, "12345"),
            )
            assertIs<CliPairingService.SubmitOutcome.InvalidCode>(
                fixture.service.submit(session.sessionId, "abc123"),
            )
        }

    @Test
    fun `submit sas pairs and closes the session on a matching code`() =
        runTest {
            val fixture = Fixture()
            val session = startedSasSession(fixture)
            every { fixture.syncManager.trustBySasCode(PEER_ID, SasCode(123456), any()) } answers {
                thirdArg<(Boolean) -> Unit>()(true)
            }

            val outcome = fixture.service.submit(session.sessionId, "123456")

            val completed = assertIs<CliPairingService.SubmitOutcome.Completed>(outcome)
            assertTrue(completed.result.paired)
            // Session is consumed
            assertIs<CliPairingService.SubmitOutcome.SessionNotFound>(
                fixture.service.submit(session.sessionId, "123456"),
            )
        }

    @Test
    fun `submit sas stays retryable on a mismatch`() =
        runTest {
            val fixture = Fixture()
            val session = startedSasSession(fixture)
            every { fixture.syncManager.trustBySasCode(PEER_ID, SasCode(111111), any()) } answers {
                thirdArg<(Boolean) -> Unit>()(false)
            }
            every { fixture.syncManager.trustBySasCode(PEER_ID, SasCode(123456), any()) } answers {
                thirdArg<(Boolean) -> Unit>()(true)
            }

            val failed =
                assertIs<CliPairingService.SubmitOutcome.Completed>(
                    fixture.service.submit(session.sessionId, "111111"),
                )
            assertFalse(failed.result.paired)
            assertTrue(failed.result.retryable)

            val retried =
                assertIs<CliPairingService.SubmitOutcome.Completed>(
                    fixture.service.submit(session.sessionId, "123456"),
                )
            assertTrue(retried.result.paired)
        }

    @Test
    fun `submit sas trusts the observed connected state over a timed-out callback`() =
        runTest {
            val fixture = Fixture()
            val session = startedSasSession(fixture)
            every { fixture.syncManager.trustBySasCode(PEER_ID, SasCode(123456), any()) } answers {
                // The background trust "succeeds late": the state flips but the
                // callback never arrives within the timeout
                fixture.runtimeInfos.value = listOf(runtimeInfo(SyncState.CONNECTED))
            }

            val outcome = fixture.service.submit(session.sessionId, "123456")

            val completed = assertIs<CliPairingService.SubmitOutcome.Completed>(outcome)
            assertTrue(completed.result.paired)
        }

    @Test
    fun `submit sas treats a false callback for an already connected device as success`() =
        runTest {
            val fixture = Fixture()
            val session = startedSasSession(fixture)
            every { fixture.syncManager.trustBySasCode(PEER_ID, SasCode(123456), any()) } answers {
                // The resolver reports false whenever state != UNVERIFIED —
                // including a retry after the pairing actually completed
                fixture.runtimeInfos.value = listOf(runtimeInfo(SyncState.CONNECTED))
                thirdArg<(Boolean) -> Unit>()(false)
            }

            val outcome = fixture.service.submit(session.sessionId, "123456")

            val completed = assertIs<CliPairingService.SubmitOutcome.Completed>(outcome)
            assertTrue(completed.result.paired)
        }

    @Test
    fun `submit v3 remembers pending recovery and retries commit on the next submit`() =
        runTest {
            val fixture = Fixture(localPairingVersion = 3)
            val session = startedV3Session(fixture)
            coEvery { fixture.pairingV3UiController.submitPin("v3-session", V3Pin("123456")) } returns
                PairingV3UiResult.Error(PairingV3UiError.NETWORK_FAILURE, PairingV3Recovery.RETRY_COMMIT)
            coEvery { fixture.pairingV3UiController.recover("v3-session") } returns
                PairingV3UiResult.Error(PairingV3UiError.NETWORK_FAILURE, PairingV3Recovery.RETRY_COMMIT)

            val first =
                assertIs<CliPairingService.SubmitOutcome.Completed>(
                    fixture.service.submit(session.sessionId, "123456"),
                )
            assertFalse(first.result.paired)
            assertTrue(first.result.retryable)

            // The commit succeeds on the retry; submitPin must not run again
            // (it would hit PAIRING_INVALID_STATE against a COMMITTING session)
            coEvery { fixture.pairingV3UiController.recover("v3-session") } returns
                PairingV3UiResult.Paired

            val second =
                assertIs<CliPairingService.SubmitOutcome.Completed>(
                    fixture.service.submit(session.sessionId, "123456"),
                )
            assertTrue(second.result.paired)
            coVerify(exactly = 1) { fixture.pairingV3UiController.submitPin("v3-session", any()) }
        }

    @Test
    fun `submit v3 answers within the trust timeout when a round-trip hangs`() =
        runTest {
            val fixture = Fixture(localPairingVersion = 3)
            val session = startedV3Session(fixture)
            coEvery { fixture.pairingV3UiController.submitPin("v3-session", V3Pin("123456")) } coAnswers {
                awaitCancellation()
            }
            coEvery { fixture.pairingV3UiController.recover("v3-session") } returns
                PairingV3UiResult.SessionReady("v3-session", 2L, 789L, "")

            val outcome = fixture.service.submit(session.sessionId, "123456")

            val completed = assertIs<CliPairingService.SubmitOutcome.Completed>(outcome)
            assertFalse(completed.result.paired)
            assertTrue(completed.result.retryable)
            assertContains(completed.result.message, "timed out")
            coVerify(exactly = 1) { fixture.pairingV3UiController.recover("v3-session") }
        }

    @Test
    fun `submit v3 retries recovery before using another pin after refresh fails`() =
        runTest {
            val fixture = Fixture(localPairingVersion = 3)
            val session = startedV3Session(fixture)
            coEvery { fixture.pairingV3UiController.submitPin("v3-session", V3Pin("111111")) } returns
                PairingV3UiResult.Error(PairingV3UiError.INCORRECT_PIN, PairingV3Recovery.REFRESH_OFFER)
            coEvery { fixture.pairingV3UiController.recover("v3-session") } returns
                PairingV3UiResult.Error(PairingV3UiError.NETWORK_FAILURE, PairingV3Recovery.REFRESH_OFFER)

            val first =
                assertIs<CliPairingService.SubmitOutcome.Completed>(
                    fixture.service.submit(session.sessionId, "111111"),
                )
            assertFalse(first.result.paired)
            assertTrue(first.result.retryable)

            coEvery { fixture.pairingV3UiController.recover("v3-session") } returns
                PairingV3UiResult.SessionReady("v3-session", 2L, 789L, "")
            coEvery { fixture.pairingV3UiController.submitPin("v3-session", V3Pin("123456")) } returns
                PairingV3UiResult.Paired

            val second =
                assertIs<CliPairingService.SubmitOutcome.Completed>(
                    fixture.service.submit(session.sessionId, "123456"),
                )
            assertTrue(second.result.paired)
            coVerify(exactly = 2) { fixture.pairingV3UiController.recover("v3-session") }
        }

    @Test
    fun `submit v3 restarts after a lost proof response and adopts the fresh session`() =
        runTest {
            val fixture = Fixture(localPairingVersion = 3)
            val session = startedV3Session(fixture)
            coEvery { fixture.pairingV3UiController.submitPin("v3-session", V3Pin("123456")) } returns
                PairingV3UiResult.Error(PairingV3UiError.NETWORK_FAILURE, PairingV3Recovery.RESTART)
            coEvery { fixture.pairingV3UiController.restart("v3-session", PEER_ID) } returns
                PairingV3UiResult.SessionReady("v3-session-2", 1L, 789L, "AB:CD")

            val first =
                assertIs<CliPairingService.SubmitOutcome.Completed>(
                    fixture.service.submit(session.sessionId, "123456"),
                )
            assertFalse(first.result.paired)
            assertTrue(first.result.retryable)
            assertContains(first.result.message, "new PIN")

            coEvery { fixture.pairingV3UiController.submitPin("v3-session-2", V3Pin("654321")) } returns
                PairingV3UiResult.Paired

            val second =
                assertIs<CliPairingService.SubmitOutcome.Completed>(
                    fixture.service.submit(session.sessionId, "654321"),
                )
            assertTrue(second.result.paired)
            coVerify(exactly = 1) { fixture.pairingV3UiController.restart("v3-session", PEER_ID) }
            coVerify(exactly = 0) { fixture.pairingV3UiController.recover(any()) }
        }

    @Test
    fun `submit v3 retries the restart before accepting another pin when it failed`() =
        runTest {
            val fixture = Fixture(localPairingVersion = 3)
            val session = startedV3Session(fixture)
            coEvery { fixture.pairingV3UiController.submitPin("v3-session", V3Pin("111111")) } returns
                PairingV3UiResult.Error(PairingV3UiError.NETWORK_FAILURE, PairingV3Recovery.RESTART)
            coEvery { fixture.pairingV3UiController.restart("v3-session", PEER_ID) } coAnswers {
                awaitCancellation()
            }

            val first =
                assertIs<CliPairingService.SubmitOutcome.Completed>(
                    fixture.service.submit(session.sessionId, "111111"),
                )
            assertFalse(first.result.paired)
            assertTrue(first.result.retryable)

            coEvery { fixture.pairingV3UiController.restart("v3-session", PEER_ID) } returns
                PairingV3UiResult.SessionReady("v3-session-2", 1L, 789L, "AB:CD")
            coEvery { fixture.pairingV3UiController.submitPin("v3-session-2", V3Pin("123456")) } returns
                PairingV3UiResult.Paired

            val second =
                assertIs<CliPairingService.SubmitOutcome.Completed>(
                    fixture.service.submit(session.sessionId, "123456"),
                )
            assertTrue(second.result.paired)
            // In-flight, once more on the timeout path, once on the next submit.
            coVerify(exactly = 3) { fixture.pairingV3UiController.restart("v3-session", PEER_ID) }
        }

    @Test
    fun `concurrent v3 submits are serialized per session`() =
        runTest {
            val fixture = Fixture(localPairingVersion = 3)
            val session = startedV3Session(fixture)
            val enteredProtocol = CompletableDeferred<Unit>()
            val releaseProtocol = CompletableDeferred<Unit>()
            coEvery { fixture.pairingV3UiController.submitPin("v3-session", V3Pin("123456")) } coAnswers {
                enteredProtocol.complete(Unit)
                releaseProtocol.await()
                PairingV3UiResult.Paired
            }

            val first = async { fixture.service.submit(session.sessionId, "123456") }
            enteredProtocol.await()
            val second = async { fixture.service.submit(session.sessionId, "123456") }
            yield()
            assertFalse(second.isCompleted)

            releaseProtocol.complete(Unit)
            val firstResult = assertIs<CliPairingService.SubmitOutcome.Completed>(first.await())
            assertTrue(firstResult.result.paired)
            assertIs<CliPairingService.SubmitOutcome.SessionNotFound>(second.await())
            coVerify(exactly = 1) {
                fixture.pairingV3UiController.submitPin("v3-session", V3Pin("123456"))
            }
        }

    @Test
    fun `cancelled v3 session is not resurrected by an in-flight submit`() =
        runTest {
            val fixture = Fixture(localPairingVersion = 3)
            val session = startedV3Session(fixture)
            val enteredProtocol = CompletableDeferred<Unit>()
            val releaseProtocol = CompletableDeferred<Unit>()
            coEvery { fixture.pairingV3UiController.submitPin("v3-session", V3Pin("123456")) } coAnswers {
                enteredProtocol.complete(Unit)
                releaseProtocol.await()
                PairingV3UiResult.Error(PairingV3UiError.NETWORK_FAILURE, PairingV3Recovery.REFRESH_OFFER)
            }
            every { fixture.pairingV3UiController.cancelDetached("v3-session") } just Runs

            val submit = async { fixture.service.submit(session.sessionId, "123456") }
            enteredProtocol.await()
            assertTrue(fixture.service.cancel(session.sessionId))
            releaseProtocol.complete(Unit)

            assertIs<CliPairingService.SubmitOutcome.SessionNotFound>(submit.await())
            assertIs<CliPairingService.SubmitOutcome.SessionNotFound>(
                fixture.service.submit(session.sessionId, "123456"),
            )
        }

    @Test
    fun `submit v3 pairs refreshes the sync state and closes the session`() =
        runTest {
            val fixture = Fixture(localPairingVersion = 3)
            val session = startedV3Session(fixture)
            coEvery { fixture.pairingV3UiController.submitPin("v3-session", V3Pin("123456")) } returns
                PairingV3UiResult.Paired

            val outcome = fixture.service.submit(session.sessionId, "123456")

            val completed = assertIs<CliPairingService.SubmitOutcome.Completed>(outcome)
            assertTrue(completed.result.paired)
            verify(exactly = 1) { fixture.syncManager.refresh(listOf(PEER_ID), any()) }
            assertIs<CliPairingService.SubmitOutcome.SessionNotFound>(
                fixture.service.submit(session.sessionId, "123456"),
            )
        }

    @Test
    fun `submit v3 refreshes the offer and stays retryable on a wrong pin`() =
        runTest {
            val fixture = Fixture(localPairingVersion = 3)
            val session = startedV3Session(fixture)
            coEvery { fixture.pairingV3UiController.submitPin("v3-session", V3Pin("111111")) } returns
                PairingV3UiResult.Error(PairingV3UiError.INCORRECT_PIN, PairingV3Recovery.REFRESH_OFFER)
            coEvery { fixture.pairingV3UiController.recover("v3-session") } returns
                PairingV3UiResult.SessionReady("v3-session", 2L, 789L, "")

            val outcome = fixture.service.submit(session.sessionId, "111111")

            val completed = assertIs<CliPairingService.SubmitOutcome.Completed>(outcome)
            assertFalse(completed.result.paired)
            assertTrue(completed.result.retryable)
            coVerify(exactly = 1) { fixture.pairingV3UiController.recover("v3-session") }
        }

    @Test
    fun `submit v3 closes the session when the peer rejects`() =
        runTest {
            val fixture = Fixture(localPairingVersion = 3)
            val session = startedV3Session(fixture)
            coEvery { fixture.pairingV3UiController.submitPin("v3-session", V3Pin("123456")) } returns
                PairingV3UiResult.Error(PairingV3UiError.REJECTED)

            val outcome = fixture.service.submit(session.sessionId, "123456")

            val completed = assertIs<CliPairingService.SubmitOutcome.Completed>(outcome)
            assertFalse(completed.result.paired)
            assertFalse(completed.result.retryable)
            assertIs<CliPairingService.SubmitOutcome.SessionNotFound>(
                fixture.service.submit(session.sessionId, "123456"),
            )
        }

    @Test
    fun `submit v3 finishes a pending commit through recover`() =
        runTest {
            val fixture = Fixture(localPairingVersion = 3)
            val session = startedV3Session(fixture)
            coEvery { fixture.pairingV3UiController.submitPin("v3-session", V3Pin("123456")) } returns
                PairingV3UiResult.Error(PairingV3UiError.NETWORK_FAILURE, PairingV3Recovery.RETRY_COMMIT)
            coEvery { fixture.pairingV3UiController.recover("v3-session") } returns
                PairingV3UiResult.Paired

            val outcome = fixture.service.submit(session.sessionId, "123456")

            val completed = assertIs<CliPairingService.SubmitOutcome.Completed>(outcome)
            assertTrue(completed.result.paired)
        }

    // endregion

    // region cancel

    @Test
    fun `cancel releases the sas exchange`() =
        runTest {
            val fixture = Fixture()
            val session = startedSasSession(fixture)

            assertTrue(fixture.service.cancel(session.sessionId))

            verify(exactly = 1) { fixture.syncManager.cancelPairing(PEER_ID) }
            assertFalse(fixture.service.cancel(session.sessionId))
        }

    @Test
    fun `cancel forwards a v3 session to the controller`() =
        runTest {
            val fixture = Fixture(localPairingVersion = 3)
            val session = startedV3Session(fixture)
            every { fixture.pairingV3UiController.cancelDetached("v3-session") } just Runs

            assertTrue(fixture.service.cancel(session.sessionId))

            verify(exactly = 1) { fixture.pairingV3UiController.cancelDetached("v3-session") }
        }

    // endregion
}
