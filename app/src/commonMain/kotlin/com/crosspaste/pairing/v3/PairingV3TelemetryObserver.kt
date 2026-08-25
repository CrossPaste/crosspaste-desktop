package com.crosspaste.pairing.v3

import com.crosspaste.dto.pairing.v3.PairingV3ErrorCode

/**
 * Redacted outcome hook for pairing v3 telemetry: [PairingProtocolV3Service]
 * reports exactly one [PairingV3TelemetryOutcome] per public entry-point call,
 * carrying only enum values — never peer identifiers, session ids, PINs, key
 * material, or exception text.
 *
 * The default is [NOOP]; desktop keeps it. Mobile injects an implementation
 * that forwards outcomes to its analytics pipeline, so cross-platform drift
 * (e.g. a `PAIRING_TRANSCRIPT_MISMATCH` / `PAIRING_PROOF_INVALID` spike) is
 * visible in the field. Observers must be cheap and non-blocking; the service
 * additionally swallows anything they throw so telemetry can never affect the
 * protocol.
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

/** One value per [PairingProtocolV3Service] public entry point. */
enum class PairingV3TelemetryStage {
    INTENT,
    PROOF,
    COMMIT,
    CANCEL,
    START,
    PIN,
    RETRY_COMMIT,
    REFRESH,
}

data class PairingV3TelemetryOutcome(
    val role: PairingV3TelemetryRole,
    val stage: PairingV3TelemetryStage,
    /** The refusal code; null on success and on transport failure. */
    val code: PairingV3ErrorCode?,
    /** True when the entry point ended in a transport failure without a v3 code. */
    val transportFailure: Boolean = false,
)
