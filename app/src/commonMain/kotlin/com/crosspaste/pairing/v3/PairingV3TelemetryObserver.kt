package com.crosspaste.pairing.v3

import com.crosspaste.dto.pairing.v3.PairingV3ErrorCode

/**
 * Redacted outcome hook for pairing v3 telemetry: [PairingProtocolV3Service]
 * reports one [PairingV3TelemetryOutcome] per protocol entry-point call
 * (acceptor: intent/proof/commit; initiator: start/pin/retryCommit/refresh),
 * plus lifecycle terminations — [PairingV3TelemetryStage.CANCEL],
 * [PairingV3TelemetryStage.REJECTED], and [PairingV3TelemetryStage.EXPIRED] —
 * each reported only when a real session actually changes state, so
 * unauthenticated requests for unknown sessions cannot amplify telemetry.
 * Outcomes carry only enum values — never peer identifiers, session ids, PINs,
 * key material, or exception text. The only unreported entry point is
 * dismissSession (removing an already-terminal card).
 *
 * The default is [NOOP]; desktop keeps it. Mobile injects an implementation
 * that forwards outcomes to its analytics pipeline, so cross-platform drift
 * (e.g. a `PAIRING_TRANSCRIPT_MISMATCH` / `PAIRING_PROOF_INVALID` spike) is
 * visible in the field. Implementations must be thread-safe (outcomes arrive
 * from concurrent coroutines), cheap, non-blocking, and must never suspend;
 * they should also rate-limit or sample before shipping outcomes off-device,
 * since refused entry points can be driven by unauthenticated LAN peers. The
 * service additionally swallows everything they throw — including
 * CancellationException, which cannot represent a real caller cancellation
 * from a synchronous callback — so telemetry can never affect the protocol.
 */
fun interface PairingV3TelemetryObserver {

    fun onOutcome(outcome: PairingV3TelemetryOutcome)

    companion object {
        val NOOP: PairingV3TelemetryObserver = PairingV3TelemetryObserver { }
    }
}

enum class PairingV3TelemetryRole {
    ACCEPTOR,
    INITIATOR,
}

/**
 * One value per instrumented [PairingProtocolV3Service] event: a protocol
 * entry point, or a lifecycle termination ([CANCEL]/[REJECTED]/[EXPIRED],
 * reported only when a real session changes state, always with the matching
 * [PairingV3ErrorCode] so they are never mistaken for successes).
 */
enum class PairingV3TelemetryStage {
    INTENT,
    PROOF,
    COMMIT,

    /**
     * Session cancelled: a peer-sent cancel on the acceptor, or a local
     * cancelSession on the initiator. Always carries `PAIRING_CANCELLED`.
     */
    CANCEL,
    START,
    PIN,
    RETRY_COMMIT,
    REFRESH,

    /**
     * Local user rejected the session (UI action, not a protocol message).
     * Always carries `PAIRING_REJECTED`.
     */
    REJECTED,

    /**
     * Session hit its TTL without completing; reported for either role.
     * Always carries `PAIRING_SESSION_EXPIRED`.
     */
    EXPIRED,
}

data class PairingV3TelemetryOutcome(
    val role: PairingV3TelemetryRole,
    val stage: PairingV3TelemetryStage,
    /**
     * Why the event was not a success: the refusal code for refused entry
     * points, or the termination code for lifecycle stages. Null means the
     * call succeeded — or, iff [transportFailure] is set, that it failed in
     * transport before any v3 code existed.
     */
    val code: PairingV3ErrorCode?,
    /** True when the entry point ended in a transport failure without a v3 code. */
    val transportFailure: Boolean = false,
) {
    init {
        require(code == null || !transportFailure) {
            "a v3 code and a transport failure are mutually exclusive"
        }
    }
}
