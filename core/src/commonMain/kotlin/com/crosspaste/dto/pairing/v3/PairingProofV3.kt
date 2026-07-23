package com.crosspaste.dto.pairing.v3

import com.crosspaste.serializer.Base64ByteArraySerializer
import kotlinx.serialization.Serializable

/**
 * Initiator proof sent after the user entered the PIN displayed by the acceptor.
 *
 * The PIN itself is absent: it only participates as the PAKE password.
 */
@Serializable
data class PairingProofV3(
    @Serializable(with = Base64ByteArraySerializer::class)
    val sessionId: ByteArray,
    val tokenGeneration: Long,
    @Serializable(with = Base64ByteArraySerializer::class)
    val initiatorPakeShare: ByteArray,
    @Serializable(with = Base64ByteArraySerializer::class)
    val transcriptHash: ByteArray,
    @Serializable(with = Base64ByteArraySerializer::class)
    val initiatorKeyConfirmation: ByteArray,
    @Serializable(with = Base64ByteArraySerializer::class)
    val initiatorIdentitySignature: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PairingProofV3) return false

        if (!sessionId.contentEquals(other.sessionId)) return false
        if (tokenGeneration != other.tokenGeneration) return false
        if (!initiatorPakeShare.contentEquals(other.initiatorPakeShare)) return false
        if (!transcriptHash.contentEquals(other.transcriptHash)) return false
        if (!initiatorKeyConfirmation.contentEquals(other.initiatorKeyConfirmation)) return false
        if (!initiatorIdentitySignature.contentEquals(other.initiatorIdentitySignature)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sessionId.contentHashCode()
        result = 31 * result + tokenGeneration.hashCode()
        result = 31 * result + initiatorPakeShare.contentHashCode()
        result = 31 * result + transcriptHash.contentHashCode()
        result = 31 * result + initiatorKeyConfirmation.contentHashCode()
        result = 31 * result + initiatorIdentitySignature.contentHashCode()
        return result
    }
}
