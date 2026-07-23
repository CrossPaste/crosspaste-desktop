package com.crosspaste.dto.pairing.v3

import com.crosspaste.serializer.Base64ByteArraySerializer
import kotlinx.serialization.Serializable

/**
 * Acceptor response proving it derived the same PAKE output for the same transcript.
 */
@Serializable
data class PairingProofResponseV3(
    @Serializable(with = Base64ByteArraySerializer::class)
    val sessionId: ByteArray,
    @Serializable(with = Base64ByteArraySerializer::class)
    val transcriptHash: ByteArray,
    @Serializable(with = Base64ByteArraySerializer::class)
    val acceptorKeyConfirmation: ByteArray,
    @Serializable(with = Base64ByteArraySerializer::class)
    val acceptorIdentitySignature: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PairingProofResponseV3) return false

        if (!sessionId.contentEquals(other.sessionId)) return false
        if (!transcriptHash.contentEquals(other.transcriptHash)) return false
        if (!acceptorKeyConfirmation.contentEquals(other.acceptorKeyConfirmation)) return false
        if (!acceptorIdentitySignature.contentEquals(other.acceptorIdentitySignature)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sessionId.contentHashCode()
        result = 31 * result + transcriptHash.contentHashCode()
        result = 31 * result + acceptorKeyConfirmation.contentHashCode()
        result = 31 * result + acceptorIdentitySignature.contentHashCode()
        return result
    }
}
