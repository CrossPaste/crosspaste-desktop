package com.crosspaste.pairing.v3

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Protocol constants for pairing v3.
 *
 * Every value in this object is part of the frozen v3 wire specification shared
 * with the mobile implementations. Changing any of them breaks interoperability
 * and invalidates the golden test vectors.
 */
object PairingV3 {

    const val PROTOCOL_VERSION: Int = 3

    /**
     * The mandatory v3 ciphersuite, FROZEN by the Phase 0 decision record
     * (`ai/docs/PAIRING_V3_PHASE0_ADR.md`, D2): RFC 9382 SPAKE2 over P-256 with
     * SHA-256, HKDF-SHA256, and HMAC-SHA256. Bound into the authenticated
     * transcript and the cross-platform golden vectors.
     */
    const val CIPHERSUITE_SPAKE2_P256: String = "SPAKE2-P256-SHA256-HKDF-SHA256-HMAC-SHA256"

    const val DOMAIN_INTENT: String = "CrossPaste-Pairing-v3-Intent"
    const val DOMAIN_OFFER: String = "CrossPaste-Pairing-v3-Offer"
    const val DOMAIN_PIN: String = "CrossPaste-Pairing-v3-PIN"
    const val DOMAIN_TRANSCRIPT: String = "CrossPaste-Pairing-v3-Transcript"

    const val LABEL_CONFIRM_INITIATOR: String = "crosspaste-v3-confirm-initiator"
    const val LABEL_CONFIRM_ACCEPTOR: String = "crosspaste-v3-confirm-acceptor"
    const val LABEL_HANDSHAKE_AEAD: String = "crosspaste-v3-handshake-aead"
    const val LABEL_COMMIT_RECEIPT: String = "crosspaste-v3-commit-receipt"

    const val CONTEXT_INITIATOR_CONFIRM: String = "INITIATOR-CONFIRM"
    const val CONTEXT_ACCEPTOR_CONFIRM: String = "ACCEPTOR-CONFIRM"
    const val CONTEXT_INITIATOR_IDENTITY: String = "INITIATOR-IDENTITY"
    const val CONTEXT_ACCEPTOR_IDENTITY: String = "ACCEPTOR-IDENTITY"
    const val CONTEXT_INITIATOR_COMMIT: String = "INITIATOR-COMMIT"
    const val CONTEXT_ACCEPTOR_COMMIT_ACK: String = "ACCEPTOR-COMMIT-ACK"

    const val SESSION_ID_SIZE: Int = 16
    const val REQUEST_ID_SIZE: Int = 16
    const val NONCE_SIZE: Int = 16
    const val PIN_SECRET_SIZE: Int = 32
    const val PIN_LENGTH: Int = 6
    const val DERIVED_KEY_SIZE: Int = 32

    // Default policy values. Marked default because the final numbers are an open
    // product decision (doc §22); endpoints may tune them, the protocol allows it.
    val DEFAULT_PIN_LIFETIME: Duration = 30.seconds
    val DEFAULT_GENERATION_GRACE: Duration = 15.seconds
    val DEFAULT_SESSION_TTL: Duration = 10.minutes
    val DEFAULT_RECEIPT_TTL: Duration = 10.minutes

    /**
     * How long a terminated (consumed) intent stays replay-blocked. Must outlive
     * the session TTL so that replaying a completed or rejected intent cannot
     * silently open a fresh session with a new PIN.
     */
    val DEFAULT_INTENT_TOMBSTONE_TTL: Duration = 30.minutes
    const val DEFAULT_MAX_ACTIVE_INCOMING_SESSIONS: Int = 4
    const val DEFAULT_MAX_INTENT_TOMBSTONES: Int = 64

    /** Hard protocol rule, not tunable: one failed proof invalidates the generation. */
    const val MAX_PROOF_ATTEMPTS_PER_GENERATION: Int = 1
}
