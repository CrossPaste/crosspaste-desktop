package com.crosspaste.pairing.v3

/**
 * One isolated pairing attempt between one acceptor and one initiator.
 *
 * Instances are immutable; the [PairingSessionStore] serializes transitions per
 * session and replaces the stored instance. Runtime attachments ([pakeSession],
 * [sessionKeys]) are carried by reference and cleared on terminal transitions.
 *
 * The transcript anchor fields ([intentHash], [offerHash], [localPakeShare],
 * [peerPakeShare], [negotiatedCapabilities]) let each endpoint rebuild the full
 * canonical transcript from its own session alone: an endpoint must never adopt a
 * transcript hash received from the peer — it recomputes and compares.
 *
 * Identity note: sessions are identified by [sessionId]; instances are never
 * compared structurally, so default data-class equality on array fields is unused.
 */
data class PairingSession(
    val sessionId: String,
    val sessionIdBytes: ByteArray,
    val role: PakeRole,
    val requestId: String,
    val peerAppInstanceId: String,
    val peerDisplayName: String,
    val peerSignPublicKey: ByteArray,
    val peerCryptPublicKey: ByteArray,
    /** Full canonical fingerprint from [PairingKeyFingerprint.of], never a display prefix. */
    val peerKeyFingerprint: String,
    val localNonce: ByteArray,
    val peerNonce: ByteArray,
    val selectedCiphersuite: String,
    val negotiatedCapabilities: List<String>,
    /** Canonical hash of the complete signed intent, from [PairingTranscriptCodec.intentHash]. */
    val intentHash: ByteArray?,
    /** Canonical hash of the complete signed offer, from [PairingTranscriptCodec.offerHash]. */
    val offerHash: ByteArray?,
    val localPakeShare: ByteArray?,
    val peerPakeShare: ByteArray?,
    val tokenGeneration: Long,
    val pin: CharArray?,
    val pinExpiresAt: Long,
    val generationFrozenUntil: Long,
    val proofAttempts: Int,
    val pakeSession: PakeSession?,
    val sessionKeys: PairingSessionKeys?,
    val transcriptHash: ByteArray?,
    val state: PairingSessionState,
    val createdAt: Long,
    val expiresAt: Long,
) {

    val isActive: Boolean
        get() = !state.isTerminal

    /**
     * Whether the current PIN generation is still usable at [nowEpochMillis]:
     * either within its lifetime, or within the negotiation grace freeze that a
     * PAKE first message obtained before expiration.
     */
    fun isPinUsable(nowEpochMillis: Long): Boolean =
        nowEpochMillis < pinExpiresAt || nowEpochMillis < generationFrozenUntil

    /**
     * Zeroes secret material in place and detaches it. References shared with
     * [alreadyCleared] are skipped so a copied session cannot destroy the same
     * native PAKE handle twice.
     */
    internal fun clearSecrets(alreadyCleared: PairingSession? = null): PairingSession {
        if (pin !== alreadyCleared?.pin) {
            runCatching { pin?.fill('\u0000') }
        }
        if (sessionKeys !== alreadyCleared?.sessionKeys) {
            runCatching { sessionKeys?.clear() }
        }
        if (pakeSession !== alreadyCleared?.pakeSession) {
            runCatching { pakeSession?.destroy() }
        }
        return copy(
            pin = null,
            sessionKeys = null,
            pakeSession = null,
        )
    }

    /** Sanitized projection for UI consumption — no keys, PAKE state, or mutable arrays. */
    fun toUiState(): PairingSessionUiState =
        PairingSessionUiState(
            sessionId = sessionId,
            role = role,
            peerDisplayName = peerDisplayName,
            peerAppInstanceId = peerAppInstanceId,
            peerKeyFingerprintDisplay = PairingKeyFingerprint.display(peerKeyFingerprint),
            pin = pin?.concatToString(),
            pinExpiresAt = pinExpiresAt,
            tokenGeneration = tokenGeneration,
            state = state,
            createdAt = createdAt,
        )
}

/**
 * What the UI layer is allowed to see about a pairing session.
 *
 * The PIN is exposed as a String because the acceptor UI must render it; that is
 * a deliberate trade-off (it is shown on screen anyway). Nothing else secret or
 * mutable crosses this boundary.
 */
data class PairingSessionUiState(
    val sessionId: String,
    val role: PakeRole,
    val peerDisplayName: String,
    val peerAppInstanceId: String,
    val peerKeyFingerprintDisplay: String,
    val pin: String?,
    val pinExpiresAt: Long,
    val tokenGeneration: Long,
    val state: PairingSessionState,
    val createdAt: Long,
)
