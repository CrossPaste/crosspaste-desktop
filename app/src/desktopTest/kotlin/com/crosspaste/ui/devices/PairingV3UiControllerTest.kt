package com.crosspaste.ui.devices

import com.crosspaste.dto.pairing.v3.PairingV3ErrorCode
import com.crosspaste.net.clientapi.UnknownError
import com.crosspaste.pairing.v3.PairingV3PinResult
import com.crosspaste.pairing.v3.PairingV3StartResult
import kotlin.test.Test
import kotlin.test.assertEquals

class PairingV3UiControllerTest {

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
}
