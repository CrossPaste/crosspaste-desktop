package com.crosspaste.pairing.v3

import com.crosspaste.dto.pairing.v3.PairingCancelV3
import com.crosspaste.dto.pairing.v3.PairingCommitAckV3
import com.crosspaste.dto.pairing.v3.PairingCommitV3
import com.crosspaste.dto.pairing.v3.PairingIntentV3
import com.crosspaste.dto.pairing.v3.PairingOfferV3
import com.crosspaste.dto.pairing.v3.PairingProofResponseV3
import com.crosspaste.dto.pairing.v3.PairingProofV3
import com.crosspaste.dto.pairing.v3.PairingV3ErrorCode
import com.crosspaste.secure.SecureKeyPair
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.utils.CryptographyUtils
import dev.whyoleg.cryptography.random.CryptographyRandom

/**
 * Transport-free pairing v3 INITIATOR engine: one instance drives exactly one
 * pairing attempt against one acceptor, producing and consuming the frozen v3
 * wire DTOs. The caller owns transport, retries, timeouts, and persistence.
 *
 * The cryptographic recipe (canonical encodings, SPAKE2 composition, key
 * schedule, MAC contexts) is byte-identical to the initiator role of the
 * desktop `PairingProtocolV3Service`; both are pinned by the shared golden
 * vectors. This engine exists for clients that cannot host the full session
 * store service — the browser extension today, mobile web surfaces tomorrow.
 *
 * Not thread-safe: drive it from a single coroutine.
 *
 * Call sequence:
 * 1. [createIntent] — POST the result to `/sync/pairing/v3/intent`.
 * 2. [acceptOffer] with the response. Null means the offer is valid; the
 *    acceptor is now displaying the PIN.
 * 3. [buildProof] with the user-entered PIN — POST to `/sync/pairing/v3/proof`.
 * 4. [verifyProofResponse] with the response. False is a hard failure
 *    (wrong acceptor key confirmation — possible MITM).
 * 5. [buildCommit] — POST to `/sync/pairing/v3/commit`. Deterministic bytes:
 *    safe to retry on transport failure.
 * 6. [verifyCommitAck] with the response. True completes the pairing; persist
 *    [acceptorCryptPublicKey] as the peer's trusted long-term key.
 *
 * PIN rotation: after `PAIRING_PIN_EXPIRED` (or one wrong PIN, which kills the
 * generation server-side), re-POST the byte-identical [intent] and feed the
 * fresh offer back through [acceptOffer]; then [buildProof] with the new PIN.
 */
class PairingV3InitiatorEngine(
    private val appInstanceId: String,
    private val secureKeyPair: SecureKeyPair,
    private val pakeProvider: PakeProvider,
    private val secureKeyPairSerializer: SecureKeyPairSerializer = SecureKeyPairSerializer(),
) {

    private enum class Phase {
        CREATED,
        INTENT_READY,
        OFFER_ACCEPTED,
        PROOF_SENT,
        PEER_CONFIRMED,
        TRUSTED,
        FAILED,
    }

    private var phase = Phase.CREATED

    private var intentMessage: PairingIntentV3? = null
    private var intentHash: ByteArray? = null

    private var offer: PairingOfferV3? = null
    private var offerHash: ByteArray? = null

    private var sessionKeys: PairingSessionKeys? = null
    private var transcriptHash: ByteArray? = null

    /** The signed intent, for byte-identical retransmission on PIN rotation. */
    val intent: PairingIntentV3
        get() = checkNotNull(intentMessage) { "createIntent has not been called" }

    /** Session id from the accepted offer (empty before [acceptOffer] succeeds). */
    val sessionId: ByteArray
        get() = offer?.sessionId?.copyOf() ?: ByteArray(0)

    val tokenGeneration: Long
        get() = offer?.tokenGeneration ?: 0L

    /** Acceptor's PIN expiry on the ACCEPTOR's clock; display hint only. */
    val pinExpiresAt: Long
        get() = offer?.pinExpiresAt ?: 0L

    /** The acceptor's long-term ECDH key; persist it after [verifyCommitAck] returns true. */
    val acceptorCryptPublicKey: ByteArray
        get() = checkNotNull(offer) { "no accepted offer" }.acceptorCryptPublicKey.copyOf()

    val acceptorSignPublicKey: ByteArray
        get() = checkNotNull(offer) { "no accepted offer" }.acceptorSignPublicKey.copyOf()

    /** Short fingerprint of the acceptor's sign key for user display. */
    suspend fun acceptorKeyFingerprintDisplay(): String =
        PairingKeyFingerprint.display(
            PairingKeyFingerprint.of(checkNotNull(offer) { "no accepted offer" }.acceptorSignPublicKey),
        )

    /** Builds and signs the intent for [targetAppInstanceId]. Callable once per engine. */
    suspend fun createIntent(
        targetAppInstanceId: String,
        displayName: String,
    ): PairingIntentV3 {
        check(phase == Phase.CREATED) { "intent already created" }
        require(targetAppInstanceId.isNotEmpty() && targetAppInstanceId != appInstanceId) {
            "invalid target app instance id"
        }
        val signPublicKey = secureKeyPair.getSignPublicKeyBytes(secureKeyPairSerializer)
        val cryptPublicKey = secureKeyPair.getCryptPublicKeyBytes(secureKeyPairSerializer)
        val unsigned =
            PairingIntentV3(
                protocolVersion = PairingV3.PROTOCOL_VERSION,
                requestId = CryptographyRandom.nextBytes(PairingV3.REQUEST_ID_SIZE),
                initiatorAppInstanceId = appInstanceId,
                targetAppInstanceId = targetAppInstanceId,
                initiatorDisplayName = displayName.take(MAX_DISPLAY_NAME_LENGTH),
                initiatorSignPublicKey = signPublicKey,
                initiatorCryptPublicKey = cryptPublicKey,
                initiatorNonce = CryptographyRandom.nextBytes(PairingV3.NONCE_SIZE),
                supportedCiphersuites = listOf(pakeProvider.ciphersuite),
                signature = ByteArray(0),
            )
        val signed =
            unsigned.copy(
                signature =
                    CryptographyUtils.signData(secureKeyPair.signKeyPair.privateKey) {
                        PairingTranscriptCodec.encodeIntentSignaturePayload(unsigned)
                    },
            )
        intentMessage = signed
        intentHash = PairingTranscriptCodec.intentHash(signed)
        phase = Phase.INTENT_READY
        return signed
    }

    /**
     * Validates the acceptor's offer against the sent intent. Returns null when
     * the offer is accepted, or the refusal code otherwise. May be called again
     * with a fresh offer after PIN rotation (re-sent intent), which resets any
     * in-flight proof state.
     */
    suspend fun acceptOffer(newOffer: PairingOfferV3): PairingV3ErrorCode? {
        check(
            phase == Phase.INTENT_READY || phase == Phase.OFFER_ACCEPTED || phase == Phase.PROOF_SENT,
        ) { "engine cannot accept an offer in phase $phase" }
        val sentIntent = intent
        val sentIntentHash = checkNotNull(intentHash)
        validateOffer(sentIntent, sentIntentHash, newOffer)?.let { code ->
            return code
        }
        // A rotated offer must belong to the session created by the first offer.
        offer?.let { previous ->
            if (!previous.sessionId.contentEquals(newOffer.sessionId)) {
                return PairingV3ErrorCode.PAIRING_TRANSCRIPT_MISMATCH
            }
        }
        clearProofState()
        offer = newOffer
        offerHash = PairingTranscriptCodec.offerHash(newOffer)
        phase = Phase.OFFER_ACCEPTED
        return null
    }

    /**
     * Runs SPAKE2 with the user-entered PIN and produces the proof message.
     * The caller owns [pin] clearing. Throws [PakeException] on cryptographic
     * failure (malformed acceptor share) and [IllegalStateException] on misuse.
     */
    suspend fun buildProof(pin: CharArray): PairingProofV3 {
        check(phase == Phase.OFFER_ACCEPTED) { "no accepted offer to prove against" }
        require(pin.size == PairingV3.PIN_LENGTH && pin.all { digit -> digit in '0'..'9' }) {
            "PIN must be ${PairingV3.PIN_LENGTH} digits"
        }
        val acceptedOffer = checkNotNull(offer)
        val ownSignPublicKey = secureKeyPair.getSignPublicKeyBytes(secureKeyPairSerializer)
        val ownCryptPublicKey = secureKeyPair.getCryptPublicKeyBytes(secureKeyPairSerializer)
        val pinContext =
            PairingTranscriptCodec.encodePinContext(
                sessionId = acceptedOffer.sessionId,
                tokenGeneration = acceptedOffer.tokenGeneration,
                acceptorAppInstanceId = acceptedOffer.acceptorAppInstanceId,
                initiatorAppInstanceId = appInstanceId,
                acceptorSignPublicKey = acceptedOffer.acceptorSignPublicKey,
                acceptorCryptPublicKey = acceptedOffer.acceptorCryptPublicKey,
                initiatorSignPublicKey = ownSignPublicKey,
                initiatorCryptPublicKey = ownCryptPublicKey,
            )
        val pakeSession =
            pakeProvider.createSession(
                role = PakeRole.INITIATOR,
                pin = pin,
                context =
                    PakeContext(
                        sessionId = acceptedOffer.sessionId,
                        initiatorAppInstanceId = appInstanceId,
                        acceptorAppInstanceId = acceptedOffer.acceptorAppInstanceId,
                        pinContext = pinContext,
                    ),
            )
        try {
            val localShare = pakeSession.localShare()
            val sharedSecret = pakeSession.deriveSharedSecret(acceptedOffer.acceptorPakeShare)
            val transcript =
                PairingTranscript(
                    protocolVersion = PairingV3.PROTOCOL_VERSION,
                    selectedCiphersuite = acceptedOffer.selectedCiphersuite,
                    sessionId = acceptedOffer.sessionId,
                    tokenGeneration = acceptedOffer.tokenGeneration,
                    initiatorAppInstanceId = appInstanceId,
                    acceptorAppInstanceId = acceptedOffer.acceptorAppInstanceId,
                    initiatorNonce = intent.initiatorNonce,
                    acceptorNonce = acceptedOffer.acceptorNonce,
                    initiatorSignPublicKey = ownSignPublicKey,
                    initiatorCryptPublicKey = ownCryptPublicKey,
                    acceptorSignPublicKey = acceptedOffer.acceptorSignPublicKey,
                    acceptorCryptPublicKey = acceptedOffer.acceptorCryptPublicKey,
                    initiatorPakeShare = localShare,
                    acceptorPakeShare = acceptedOffer.acceptorPakeShare,
                    intentHash = checkNotNull(intentHash),
                    offerHash = checkNotNull(offerHash),
                    negotiatedCapabilities = NEGOTIATED_CAPABILITIES,
                )
            val hash = PairingTranscriptCodec.transcriptHash(transcript)
            val keys = PairingKeySchedule.deriveSessionKeys(hash, sharedSecret)
            sharedSecret.fill(0)
            sessionKeys?.clear()
            sessionKeys = keys
            transcriptHash = hash
            phase = Phase.PROOF_SENT
            return PairingProofV3(
                sessionId = acceptedOffer.sessionId,
                tokenGeneration = acceptedOffer.tokenGeneration,
                initiatorPakeShare = localShare,
                transcriptHash = hash,
                initiatorKeyConfirmation = PairingKeySchedule.initiatorConfirmation(keys, hash),
                initiatorIdentitySignature =
                    CryptographyUtils.signData(secureKeyPair.signKeyPair.privateKey) {
                        PairingKeySchedule.identitySignaturePayload(PakeRole.INITIATOR, hash)
                    },
            )
        } finally {
            pakeSession.destroy()
        }
    }

    /**
     * Verifies the acceptor's key confirmation and identity signature. False is
     * a hard failure (the engine enters FAILED): the acceptor — or a MITM —
     * could not confirm the same key, and the pairing must not proceed.
     */
    suspend fun verifyProofResponse(response: PairingProofResponseV3): Boolean {
        check(phase == Phase.PROOF_SENT) { "no proof in flight" }
        val acceptedOffer = checkNotNull(offer)
        val keys = checkNotNull(sessionKeys)
        val hash = checkNotNull(transcriptHash)
        val valid =
            response.sessionId.contentEquals(acceptedOffer.sessionId) &&
                response.transcriptHash.contentEquals(hash) &&
                PairingKeySchedule.constantTimeEquals(
                    PairingKeySchedule.acceptorConfirmation(keys, hash),
                    response.acceptorKeyConfirmation,
                ) &&
                runCatching {
                    val peerSignKey =
                        secureKeyPairSerializer.decodeSignPublicKey(acceptedOffer.acceptorSignPublicKey)
                    CryptographyUtils.verifyData(peerSignKey, response.acceptorIdentitySignature) {
                        PairingKeySchedule.identitySignaturePayload(PakeRole.ACCEPTOR, hash)
                    }
                }.getOrDefault(false)
        phase =
            if (valid) {
                Phase.PEER_CONFIRMED
            } else {
                clearProofState()
                Phase.FAILED
            }
        return valid
    }

    /** Deterministic commit message; safe to rebuild for bounded retries. */
    suspend fun buildCommit(): PairingCommitV3 {
        check(phase == Phase.PEER_CONFIRMED) { "peer is not confirmed" }
        val acceptedOffer = checkNotNull(offer)
        val keys = checkNotNull(sessionKeys)
        val hash = checkNotNull(transcriptHash)
        return PairingCommitV3(
            sessionId = acceptedOffer.sessionId,
            transcriptHash = hash,
            commitMac = PairingKeySchedule.commitMac(keys, hash),
        )
    }

    /**
     * Verifies the acceptor's commit receipt. True completes the pairing; the
     * caller must then persist [acceptorCryptPublicKey] as trusted.
     */
    suspend fun verifyCommitAck(ack: PairingCommitAckV3): Boolean {
        check(phase == Phase.PEER_CONFIRMED) { "no commit in flight" }
        val acceptedOffer = checkNotNull(offer)
        val keys = checkNotNull(sessionKeys)
        val hash = checkNotNull(transcriptHash)
        val valid =
            ack.sessionId.contentEquals(acceptedOffer.sessionId) &&
                ack.transcriptHash.contentEquals(hash) &&
                PairingKeySchedule.constantTimeEquals(
                    PairingKeySchedule.receiptMac(keys, hash),
                    ack.receiptMac,
                )
        if (valid) {
            phase = Phase.TRUSTED
            clearProofState()
        } else {
            clearProofState()
            phase = Phase.FAILED
        }
        return valid
    }

    /** Advisory cancel message for the current session, if one exists. */
    fun buildCancel(): PairingCancelV3? = offer?.let { PairingCancelV3(sessionId = it.sessionId) }

    /** Clears key material; the engine is unusable afterwards. */
    fun destroy() {
        clearProofState()
        phase = Phase.FAILED
    }

    private fun clearProofState() {
        sessionKeys?.clear()
        sessionKeys = null
        transcriptHash = null
    }

    private suspend fun validateOffer(
        sentIntent: PairingIntentV3,
        sentIntentHash: ByteArray,
        offer: PairingOfferV3,
    ): PairingV3ErrorCode? {
        if (offer.protocolVersion != PairingV3.PROTOCOL_VERSION) {
            return PairingV3ErrorCode.PAIRING_VERSION_UNSUPPORTED
        }
        if (offer.selectedCiphersuite !in sentIntent.supportedCiphersuites) {
            return PairingV3ErrorCode.PAIRING_CIPHERSUITE_UNSUPPORTED
        }
        if (offer.sessionId.size != PairingV3.SESSION_ID_SIZE ||
            offer.acceptorNonce.size != PairingV3.NONCE_SIZE ||
            offer.tokenGeneration < 1L ||
            !isCanonicalPakeShare(offer.acceptorPakeShare) ||
            !offer.requestHash.contentEquals(sentIntentHash) ||
            offer.initiatorAppInstanceId != sentIntent.initiatorAppInstanceId ||
            offer.acceptorAppInstanceId != sentIntent.targetAppInstanceId
        ) {
            return PairingV3ErrorCode.PAIRING_TRANSCRIPT_MISMATCH
        }
        if (offer.acceptorSignPublicKey.isEmpty() ||
            offer.acceptorSignPublicKey.size > MAX_KEY_SIZE ||
            offer.acceptorCryptPublicKey.isEmpty() ||
            offer.acceptorCryptPublicKey.size > MAX_KEY_SIZE ||
            offer.signature.isEmpty() ||
            offer.signature.size > MAX_SIGNATURE_SIZE
        ) {
            return PairingV3ErrorCode.PAIRING_IDENTITY_INVALID
        }
        val signatureValid =
            runCatching {
                val acceptorSignKey = secureKeyPairSerializer.decodeSignPublicKey(offer.acceptorSignPublicKey)
                runCatching { secureKeyPairSerializer.decodeCryptPublicKey(offer.acceptorCryptPublicKey) }
                    .getOrNull() ?: return@runCatching false
                CryptographyUtils.verifyData(acceptorSignKey, offer.signature) {
                    PairingTranscriptCodec.encodeOfferSignaturePayload(offer)
                }
            }.getOrDefault(false)
        if (!signatureValid) {
            return PairingV3ErrorCode.PAIRING_IDENTITY_INVALID
        }
        return null
    }

    private fun isCanonicalPakeShare(share: ByteArray): Boolean =
        share.size == PairingV3.PAKE_SHARE_SIZE &&
            share[0] == PairingV3.PAKE_SHARE_UNCOMPRESSED_PREFIX

    companion object {
        /** Frozen as empty until the first real capability negotiation ships (matches desktop). */
        private val NEGOTIATED_CAPABILITIES: List<String> = emptyList()

        private const val MAX_KEY_SIZE = 256
        private const val MAX_SIGNATURE_SIZE = 256
        private const val MAX_DISPLAY_NAME_LENGTH = 128
    }
}
