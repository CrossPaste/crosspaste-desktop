package com.crosspaste.ui.devices

import com.crosspaste.pairing.v3.PairingSessionState
import com.crosspaste.pairing.v3.PairingSessionUiState
import com.crosspaste.pairing.v3.PakeRole
import com.crosspaste.sync.SyncManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PairingV3DialogStateTest {

    @Test
    fun incorrectPin_autoRefreshesOfferAndRequestsFreshInput() =
        runTest {
            val controller = mockk<PairingV3UiController>()
            val state =
                PairingV3DialogState(
                    appInstanceId = "peer",
                    controller = controller,
                    syncManager = mockk<SyncManager>(relaxed = true),
                    coroutineScope = this,
                    incorrectPinFeedbackDuration = 1.seconds,
                )
            coEvery {
                controller.submitPin("session", any())
            } returns
                PairingV3UiResult.Error(
                    PairingV3UiError.INCORRECT_PIN,
                    PairingV3Recovery.REFRESH_OFFER,
                )
            coEvery {
                controller.recover("session")
            } returns
                PairingV3UiResult.SessionReady(
                    sessionId = "session",
                    tokenGeneration = 2,
                    pinExpiresAt = 60_000,
                    peerKeyFingerprintDisplay = "aabbccdd",
                )
            coEvery { controller.cancel("session") } returns true

            state.submitPin("session", "123456")
            runCurrent()

            assertEquals(PairingV3UiError.INCORRECT_PIN, state.uiError)
            assertEquals(PairingV3Recovery.NONE, state.recovery)
            assertTrue(state.isLoading)
            assertEquals(0, state.inputResetGeneration)

            state.cancel(
                PairingSessionUiState(
                    sessionId = "session",
                    role = PakeRole.INITIATOR,
                    peerDisplayName = "Peer",
                    peerAppInstanceId = "peer",
                    peerKeyFingerprintDisplay = "aabbccdd",
                    pin = null,
                    pinExpiresAt = 60_000,
                    tokenGeneration = 1,
                    state = PairingSessionState.PIN_AVAILABLE,
                    createdAt = 0,
                ),
            )
            runCurrent()
            coVerify(exactly = 0) { controller.cancel("session") }

            advanceTimeBy(1_000)
            runCurrent()

            coVerify(exactly = 1) { controller.recover("session") }
            assertEquals(1, state.inputResetGeneration)
            assertFalse(state.isLoading)
            assertEquals(null, state.uiError)
        }

    @Test
    fun incorrectPin_autoRefreshFailureLeavesManualRetryPath() =
        runTest {
            val controller = mockk<PairingV3UiController>()
            val state =
                PairingV3DialogState(
                    appInstanceId = "peer",
                    controller = controller,
                    syncManager = mockk<SyncManager>(relaxed = true),
                    coroutineScope = this,
                    incorrectPinFeedbackDuration = 1.seconds,
                )
            coEvery {
                controller.submitPin("session", any())
            } returns
                PairingV3UiResult.Error(
                    PairingV3UiError.INCORRECT_PIN,
                    PairingV3Recovery.REFRESH_OFFER,
                )
            coEvery {
                controller.recover("session")
            } returns
                PairingV3UiResult.Error(
                    PairingV3UiError.NETWORK_FAILURE,
                    PairingV3Recovery.REFRESH_OFFER,
                )

            state.submitPin("session", "123456")
            advanceTimeBy(1_000)
            runCurrent()

            assertEquals(1, state.inputResetGeneration)
            assertEquals(PairingV3UiError.NETWORK_FAILURE, state.uiError)
            assertEquals(PairingV3Recovery.REFRESH_OFFER, state.recovery)
            assertTrue(state.recovery != PairingV3Recovery.NONE)
        }

    @Test
    fun restartRecovery_retryRestartsInsteadOfRefreshing() =
        runTest {
            val controller = mockk<PairingV3UiController>()
            val state =
                PairingV3DialogState(
                    appInstanceId = "peer",
                    controller = controller,
                    syncManager = mockk<SyncManager>(relaxed = true),
                    coroutineScope = this,
                )
            coEvery { controller.submitPin("session", any()) } returns
                PairingV3UiResult.Error(
                    PairingV3UiError.NETWORK_FAILURE,
                    PairingV3Recovery.RESTART,
                )
            coEvery { controller.restart("session", "peer") } returns
                PairingV3UiResult.SessionReady(
                    sessionId = "session-2",
                    tokenGeneration = 1,
                    pinExpiresAt = 60_000,
                    peerKeyFingerprintDisplay = "aabbccdd",
                )

            state.submitPin("session", "123456")
            runCurrent()
            assertEquals(PairingV3UiError.NETWORK_FAILURE, state.uiError)
            assertEquals(PairingV3Recovery.RESTART, state.recovery)

            state.retry("session")
            runCurrent()

            coVerify(exactly = 1) { controller.restart("session", "peer") }
            coVerify(exactly = 0) { controller.recover(any()) }
            assertEquals(null, state.uiError)
            assertEquals(PairingV3Recovery.NONE, state.recovery)
            assertFalse(state.isLoading)
        }
}
