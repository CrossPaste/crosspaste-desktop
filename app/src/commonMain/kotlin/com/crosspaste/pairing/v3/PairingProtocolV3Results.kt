package com.crosspaste.pairing.v3

import com.crosspaste.dto.pairing.v3.PairingV3ErrorCode
import com.crosspaste.net.clientapi.ClientApiResult

/**
 * Outcome of one acceptor-side protocol step. [Refused] carries the wire error
 * code exactly as it must be surfaced to the peer — proof-stage internals are
 * already collapsed to [PairingV3ErrorCode.PAIRING_PROOF_INVALID] by the service.
 */
sealed interface PairingV3ServerResult<out T> {

    data class Ok<T>(
        val value: T,
    ) : PairingV3ServerResult<T>

    data class Refused(
        val code: PairingV3ErrorCode,
    ) : PairingV3ServerResult<Nothing>
}

/** Outcome of the initiator-side intent → offer exchange. */
sealed interface PairingV3StartResult {

    /** The session is created locally and waits for the user-entered PIN. */
    data class Started(
        val sessionId: String,
        val tokenGeneration: Long,
        val pinExpiresAt: Long,
        val peerKeyFingerprintDisplay: String,
    ) : PairingV3StartResult

    data class Refused(
        val code: PairingV3ErrorCode,
    ) : PairingV3StartResult

    /** Transport failure without a v3 error code; the caller may retry. */
    data class NetworkError(
        val failure: ClientApiResult,
    ) : PairingV3StartResult
}

/** Outcome of the initiator-side proof → commit sequence. */
sealed interface PairingV3PinResult {

    /** Both peers committed; the peer's crypt key is persisted locally. */
    data class Paired(
        val peerAppInstanceId: String,
    ) : PairingV3PinResult

    data class Refused(
        val code: PairingV3ErrorCode,
    ) : PairingV3PinResult

    /**
     * Transport failure. When [commitPending] is true the local session holds a
     * verified transcript in COMMITTING state and `retryCommit` can complete the
     * pairing without a new PIN; otherwise the generation was invalidated and the
     * caller should refresh the offer and ask for the (possibly rotated) PIN again.
     */
    data class NetworkError(
        val failure: ClientApiResult,
        val commitPending: Boolean,
    ) : PairingV3PinResult
}

/** Outcome of re-fetching the current offer after rotation or a transport failure. */
sealed interface PairingV3RefreshResult {

    data class Refreshed(
        val tokenGeneration: Long,
        val pinExpiresAt: Long,
    ) : PairingV3RefreshResult

    data class Refused(
        val code: PairingV3ErrorCode,
    ) : PairingV3RefreshResult

    data class NetworkError(
        val failure: ClientApiResult,
    ) : PairingV3RefreshResult
}
