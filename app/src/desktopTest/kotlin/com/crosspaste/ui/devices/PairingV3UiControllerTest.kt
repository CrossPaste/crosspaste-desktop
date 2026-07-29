package com.crosspaste.ui.devices

import com.crosspaste.dto.pairing.v3.PairingV3ErrorCode
import com.crosspaste.net.clientapi.UnknownError
import com.crosspaste.pairing.v3.PairingAcceptanceWindow
import com.crosspaste.pairing.v3.PairingProtocolV3Service
import com.crosspaste.pairing.v3.PairingSessionState
import com.crosspaste.pairing.v3.PairingSessionUiState
import com.crosspaste.pairing.v3.PairingV3PinResult
import com.crosspaste.pairing.v3.PairingV3StartResult
import com.crosspaste.pairing.v3.PakeRole
import com.crosspaste.sync.SyncHandler
import com.crosspaste.sync.SyncManager
import com.crosspaste.sync.SyncTestFixtures.createUnverifiedSyncRuntimeInfo
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PairingV3UiControllerTest {

    @Test
    fun startPairingRequestsRemotePairingScreenBeforeSendingIntent() =
        runTest {
            val peerAppInstanceId = "peer-app"
            val syncRuntimeInfo =
                createUnverifiedSyncRuntimeInfo(
                    appInstanceId = peerAppInstanceId,
                    hostAddress = "192.168.1.10",
                )
            val syncHandler = mockk<SyncHandler>()
            val syncManager = mockk<SyncManager>()
            val pairingProtocolV3Service = mockk<PairingProtocolV3Service>()

            every { syncManager.getSyncHandler(peerAppInstanceId) } returns syncHandler
            every { syncHandler.currentSyncRuntimeInfo } returns syncRuntimeInfo
            coEvery { syncHandler.getConnectHostAddress() } returns "192.168.1.10"
            coEvery { syncHandler.showPairingCode() } returns Unit
            every { pairingProtocolV3Service.uiSessionsFlow } returns MutableStateFlow(emptyList())
            every { pairingProtocolV3Service.acceptanceWindow } returns PairingAcceptanceWindow()
            coEvery {
                pairingProtocolV3Service.startPairing(
                    targetAppInstanceId = peerAppInstanceId,
                    targetDisplayName = any(),
                    toUrl = any(),
                )
            } returns PairingV3StartResult.NetworkError(UnknownError)

            DefaultPairingV3UiController(pairingProtocolV3Service, syncManager)
                .startPairing(peerAppInstanceId)

            coVerifyOrder {
                syncHandler.showPairingCode()
                pairingProtocolV3Service.startPairing(
                    targetAppInstanceId = peerAppInstanceId,
                    targetDisplayName = any(),
                    toUrl = any(),
                )
            }
        }

    @Test
    fun proofFailureIsPresentedAsIncorrectPinWithOfferRefresh() {
        val result =
            PairingV3PinResult
                .Refused(PairingV3ErrorCode.PAIRING_PROOF_INVALID)
                .toUiResult()

        assertEquals(
            PairingV3UiResult.Error(
                PairingV3UiError.INCORRECT_PIN,
                PairingV3Recovery.REFRESH_OFFER,
            ),
            result,
        )
    }

    @Test
    fun expiredPinIsDistinctFromIncorrectPin() {
        val result =
            PairingV3PinResult
                .Refused(PairingV3ErrorCode.PAIRING_PIN_EXPIRED)
                .toUiResult()

        assertEquals(
            PairingV3UiResult.Error(
                PairingV3UiError.PIN_EXPIRED,
                PairingV3Recovery.REFRESH_OFFER,
            ),
            result,
        )
    }

    @Test
    fun proofNetworkFailureRefreshesOffer() {
        val result =
            PairingV3PinResult
                .NetworkError(UnknownError, commitPending = false)
                .toUiResult()

        assertEquals(
            PairingV3UiResult.Error(
                PairingV3UiError.NETWORK_FAILURE,
                PairingV3Recovery.REFRESH_OFFER,
            ),
            result,
        )
    }

    @Test
    fun commitNetworkFailureRetriesCommitWithoutNewPin() {
        val result =
            PairingV3PinResult
                .NetworkError(UnknownError, commitPending = true)
                .toUiResult()

        assertEquals(
            PairingV3UiResult.Error(
                PairingV3UiError.NETWORK_FAILURE,
                PairingV3Recovery.RETRY_COMMIT,
            ),
            result,
        )
    }

    @Test
    fun startNetworkFailureRetriesStart() {
        val result = PairingV3StartResult.NetworkError(UnknownError).toUiResult()

        assertEquals(
            PairingV3UiResult.Error(
                PairingV3UiError.NETWORK_FAILURE,
                PairingV3Recovery.RETRY_START,
            ),
            result,
        )
    }

    @Test
    fun closedAcceptanceWindowIsNotReportedAsUnsupportedAndCanBeRetried() {
        val result =
            PairingV3StartResult
                .Refused(PairingV3ErrorCode.PAIRING_DISABLED)
                .toUiResult()

        // The acceptor may not be accepting yet; the user must be able to retry.
        assertEquals(
            PairingV3UiResult.Error(
                PairingV3UiError.NOT_ACCEPTING,
                PairingV3Recovery.RETRY_START,
            ),
            result,
        )
    }

    @Test
    fun rateLimitedStartOffersRetry() {
        val result =
            PairingV3StartResult
                .Refused(PairingV3ErrorCode.PAIRING_RATE_LIMITED)
                .toUiResult()

        assertEquals(
            PairingV3UiResult.Error(PairingV3UiError.RATE_LIMITED, PairingV3Recovery.RETRY_START),
            result,
        )
    }

    @Test
    fun rateLimitedProofRefreshesOfferSoTheUserIsNotStuck() {
        val result =
            PairingV3PinResult
                .Refused(PairingV3ErrorCode.PAIRING_RATE_LIMITED)
                .toUiResult()

        assertEquals(
            PairingV3UiResult.Error(PairingV3UiError.RATE_LIMITED, PairingV3Recovery.REFRESH_OFFER),
            result,
        )
    }

    @Test
    fun fatalIdentityErrorHasNoRecoveryPath() {
        val result =
            PairingV3PinResult
                .Refused(PairingV3ErrorCode.PAIRING_IDENTITY_INVALID)
                .toUiResult()

        assertEquals(
            PairingV3UiResult.Error(PairingV3UiError.IDENTITY_INVALID, PairingV3Recovery.NONE),
            result,
        )
    }

    @Test
    fun countdownRoundsUpAndNeverBecomesNegative() {
        assertEquals(2L, secondsUntil(expiresAt = 2_001L, now = 1_000L))
        assertEquals(1L, secondsUntil(expiresAt = 1_001L, now = 1_000L))
        assertEquals(0L, secondsUntil(expiresAt = 1_000L, now = 1_000L))
        assertEquals(0L, secondsUntil(expiresAt = 999L, now = 1_000L))
    }

    @Test
    fun acceptorTokenCardsKeepConcurrentDevicePinsIndependent() {
        val first = pairingSession("first", PakeRole.ACCEPTOR, "123456")
        val second = pairingSession("second", PakeRole.ACCEPTOR, "654321")
        val initiator = pairingSession("initiator", PakeRole.INITIATOR, null)

        val incoming = acceptorPairingSessions(listOf(first, initiator, second))

        assertEquals(listOf("first", "second"), incoming.map { it.sessionId })
        assertEquals(listOf("123456", "654321"), incoming.map { it.pin })
    }

    @Test
    fun trustedSessionAutoDismissesWithoutClosingOtherDeviceCards() {
        val trusted = pairingSession("trusted", PakeRole.ACCEPTOR, null, PairingSessionState.TRUSTED)
        val stillPairing = pairingSession("pairing", PakeRole.ACCEPTOR, "123456")
        val rejected = pairingSession("rejected", PakeRole.ACCEPTOR, null, PairingSessionState.REJECTED)
        val sessions = listOf(trusted, stillPairing, rejected)

        assertEquals(listOf("trusted"), trustedPairingSessionIds(sessions))
        // Rejected stays visible so the user sees the failure; only TRUSTED closes itself.
        assertEquals(listOf("pairing", "rejected"), pairingTokenCardSessions(sessions).map { it.sessionId })
    }

    private fun pairingSession(
        sessionId: String,
        role: PakeRole,
        pin: String?,
        state: PairingSessionState = PairingSessionState.PIN_AVAILABLE,
    ) = PairingSessionUiState(
        sessionId = sessionId,
        role = role,
        peerDisplayName = sessionId,
        peerAppInstanceId = "$sessionId-app",
        peerKeyFingerprintDisplay = "aabbccdd",
        pin = pin,
        pinExpiresAt = 60_000,
        tokenGeneration = 1,
        state = state,
        createdAt = 0,
    )
}
