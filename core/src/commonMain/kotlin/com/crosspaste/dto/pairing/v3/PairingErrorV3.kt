package com.crosspaste.dto.pairing.v3

import kotlinx.serialization.Serializable

/**
 * Explicit v3 error codes.
 *
 * Responses must not disclose whether a guessed PIN was close or which sub-proof
 * failed; detailed diagnostics belong in local redacted logs only.
 */
@Serializable
enum class PairingV3ErrorCode {
    PAIRING_DISABLED,
    PAIRING_VERSION_UNSUPPORTED,
    PAIRING_CIPHERSUITE_UNSUPPORTED,
    PAIRING_CAPACITY_EXCEEDED,
    PAIRING_RATE_LIMITED,
    PAIRING_SESSION_NOT_FOUND,
    PAIRING_SESSION_EXPIRED,
    PAIRING_SESSION_CONSUMED,
    PAIRING_PIN_EXPIRED,
    PAIRING_PROOF_INVALID,
    PAIRING_TRANSCRIPT_MISMATCH,
    PAIRING_IDENTITY_INVALID,
    PAIRING_INVALID_STATE,
    PAIRING_REJECTED,
    PAIRING_CANCELLED,
}

@Serializable
data class PairingErrorV3(
    val code: PairingV3ErrorCode,
)
