package com.crosspaste.ui.devices

import com.crosspaste.pairing.v3.PairingSessionState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locks the derived button/lock logic of the initiator dialog. This is exactly
 * where an earlier error state could strand the user behind a permanently-disabled
 * Confirm with no retry path, so every state combination is asserted directly.
 */
class PairingV3DialogModelTest {

    private fun model(
        sessionState: PairingSessionState? = PairingSessionState.PIN_AVAILABLE,
        isLoading: Boolean = false,
        uiError: PairingV3UiError? = null,
        recovery: PairingV3Recovery = PairingV3Recovery.NONE,
        pinComplete: Boolean = false,
    ) = PairingV3DialogModel(
        deviceDisplayName = "peer",
        peerKeyFingerprint = "ab12cd34",
        sessionState = sessionState,
        isLoading = isLoading,
        uiError = uiError,
        recovery = recovery,
        pinComplete = pinComplete,
    )

    @Test
    fun freshSessionWithCompletePinCanSubmitAndShowsConfirm() {
        val m = model(pinComplete = true)
        assertTrue(m.canSubmit)
        assertTrue(m.showConfirm)
        assertFalse(m.canRetry)
        assertFalse(m.inputLocked)
    }

    @Test
    fun incompletePinCannotSubmitButConfirmStillShows() {
        val m = model(pinComplete = false)
        assertFalse(m.canSubmit)
        assertTrue(m.showConfirm)
        assertFalse(m.inputLocked)
    }

    @Test
    fun recoverableErrorShowsRetryNotAStuckConfirm() {
        val m =
            model(
                uiError = PairingV3UiError.INCORRECT_PIN,
                recovery = PairingV3Recovery.REFRESH_OFFER,
                pinComplete = true,
            )
        assertTrue(m.canRetry)
        // A retry path exists, so the disabled Confirm must not be rendered.
        assertFalse(m.showConfirm)
        assertFalse(m.canSubmit)
        assertTrue(m.inputLocked)
    }

    @Test
    fun retryStartIsOfferedEvenWithoutASession() {
        val m =
            model(
                sessionState = null,
                uiError = PairingV3UiError.RATE_LIMITED,
                recovery = PairingV3Recovery.RETRY_START,
            )
        assertTrue(m.canRetry)
        assertFalse(m.showConfirm)
        assertFalse(m.hasSession)
    }

    @Test
    fun refreshRecoveryWithoutSessionOffersNoRetry() {
        val m =
            model(
                sessionState = null,
                uiError = PairingV3UiError.NETWORK_FAILURE,
                recovery = PairingV3Recovery.REFRESH_OFFER,
            )
        // REFRESH_OFFER needs a session to recover; with none there is no retry.
        assertFalse(m.canRetry)
        assertFalse(m.showConfirm)
    }

    @Test
    fun unrecoverableErrorLeavesOnlyCancel() {
        val m =
            model(
                uiError = PairingV3UiError.IDENTITY_INVALID,
                recovery = PairingV3Recovery.NONE,
                pinComplete = true,
            )
        assertFalse(m.canRetry)
        assertFalse(m.showConfirm)
        assertFalse(m.canSubmit)
        assertTrue(m.inputLocked)
    }

    @Test
    fun loadingLocksTheInputRow() {
        // The Confirm button is disabled during loading by DialogActionButton's own
        // isLoading param, not by canSubmit; the model's job here is to lock the
        // token input row while an action is in flight.
        val m = model(isLoading = true, pinComplete = true)
        assertTrue(m.inputLocked)
    }

    @Test
    fun terminalSessionIsNotSubmittableAndHidesConfirm() {
        val m = model(sessionState = PairingSessionState.TRUSTED, pinComplete = true)
        assertTrue(m.isTerminal)
        assertFalse(m.canSubmit)
        assertFalse(m.showConfirm)
    }
}
