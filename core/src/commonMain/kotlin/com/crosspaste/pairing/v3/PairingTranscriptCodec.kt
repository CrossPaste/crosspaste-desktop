package com.crosspaste.pairing.v3

import com.crosspaste.dto.pairing.v3.PairingIntentV3
import com.crosspaste.dto.pairing.v3.PairingOfferV3
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256

/**
 * Canonical encodings for every byte sequence that pairing v3 signs, MACs, or hashes.
 *
 * Field ids inside each encoding are frozen by golden test vectors; changing an id,
 * the field order, or the domain string is a wire-breaking protocol change.
 */
object PairingTranscriptCodec {

    private val sha256 = CryptographyProvider.Default.get(SHA256)

    /** Payload covered by the intent signature: every intent field except the signature. */
    fun encodeIntentSignaturePayload(intent: PairingIntentV3): ByteArray =
        CanonicalWriter(PairingV3.DOMAIN_INTENT)
            .field(0, intent.protocolVersion)
            .field(1, intent.requestId)
            .field(2, intent.initiatorAppInstanceId)
            .field(3, intent.targetAppInstanceId)
            .field(4, intent.initiatorDisplayName)
            .field(5, intent.initiatorSignPublicKey)
            .field(6, intent.initiatorCryptPublicKey)
            .field(7, intent.initiatorNonce)
            .field(8, intent.supportedCiphersuites)
            .build()

    /** Payload covered by the offer signature: every offer field except the signature. */
    fun encodeOfferSignaturePayload(offer: PairingOfferV3): ByteArray =
        CanonicalWriter(PairingV3.DOMAIN_OFFER)
            .field(0, offer.protocolVersion)
            .field(1, offer.selectedCiphersuite)
            .field(2, offer.sessionId)
            .field(3, offer.requestHash)
            .field(4, offer.tokenGeneration)
            .field(5, offer.pinExpiresAt)
            .field(6, offer.initiatorAppInstanceId)
            .field(7, offer.acceptorAppInstanceId)
            .field(8, offer.acceptorSignPublicKey)
            .field(9, offer.acceptorCryptPublicKey)
            .field(10, offer.acceptorNonce)
            .field(11, offer.acceptorPakeShare)
            .build()

    /**
     * Canonical PIN derivation context. Binding the PIN to the exact session, generation,
     * and both identities makes concurrent peers receive unrelated PINs; security still
     * rests on the PAKE transcript, not on this derivation.
     */
    fun encodePinContext(
        sessionId: ByteArray,
        tokenGeneration: Long,
        acceptorAppInstanceId: String,
        initiatorAppInstanceId: String,
        acceptorSignPublicKey: ByteArray,
        acceptorCryptPublicKey: ByteArray,
        initiatorSignPublicKey: ByteArray,
        initiatorCryptPublicKey: ByteArray,
    ): ByteArray =
        CanonicalWriter(PairingV3.DOMAIN_PIN)
            .field(0, PairingV3.PROTOCOL_VERSION)
            .field(1, sessionId)
            .field(2, tokenGeneration)
            .field(3, acceptorAppInstanceId)
            .field(4, initiatorAppInstanceId)
            .field(5, acceptorSignPublicKey)
            .field(6, acceptorCryptPublicKey)
            .field(7, initiatorSignPublicKey)
            .field(8, initiatorCryptPublicKey)
            .build()

    fun encodeTranscript(transcript: PairingTranscript): ByteArray =
        CanonicalWriter(PairingV3.DOMAIN_TRANSCRIPT)
            .field(0, transcript.protocolVersion)
            .field(1, transcript.selectedCiphersuite)
            .field(2, transcript.sessionId)
            .field(3, transcript.tokenGeneration)
            .field(4, transcript.initiatorAppInstanceId)
            .field(5, transcript.acceptorAppInstanceId)
            .field(6, transcript.initiatorNonce)
            .field(7, transcript.acceptorNonce)
            .field(8, transcript.initiatorSignPublicKey)
            .field(9, transcript.initiatorCryptPublicKey)
            .field(10, transcript.acceptorSignPublicKey)
            .field(11, transcript.acceptorCryptPublicKey)
            .field(12, transcript.initiatorPakeShare)
            .field(13, transcript.acceptorPakeShare)
            .field(14, transcript.intentHash)
            .field(15, transcript.offerHash)
            .field(16, transcript.negotiatedCapabilities)
            .build()

    suspend fun transcriptHash(transcript: PairingTranscript): ByteArray =
        sha256.hasher().hash(encodeTranscript(transcript))

    /** Hash of the complete intent including its signature, referenced as `requestHash` in the offer. */
    suspend fun intentHash(intent: PairingIntentV3): ByteArray =
        sha256.hasher().hash(
            encodeIntentSignaturePayload(intent) + CanonicalWriter.u32(intent.signature.size) + intent.signature,
        )

    /** Hash of the complete offer including its signature, bound into the transcript. */
    suspend fun offerHash(offer: PairingOfferV3): ByteArray =
        sha256.hasher().hash(
            encodeOfferSignaturePayload(offer) + CanonicalWriter.u32(offer.signature.size) + offer.signature,
        )
}
