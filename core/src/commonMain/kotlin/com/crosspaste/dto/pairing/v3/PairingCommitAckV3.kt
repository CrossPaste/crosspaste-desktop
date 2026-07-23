package com.crosspaste.dto.pairing.v3

import com.crosspaste.serializer.Base64ByteArraySerializer
import kotlinx.serialization.Serializable

/**
 * Authenticated receipt returned by the acceptor after persisting the initiator key.
 *
 * The initiator persists the acceptor's long-term crypt public key only after
 * verifying this receipt.
 */
@Serializable
data class PairingCommitAckV3(
    @Serializable(with = Base64ByteArraySerializer::class)
    val sessionId: ByteArray,
    @Serializable(with = Base64ByteArraySerializer::class)
    val transcriptHash: ByteArray,
    @Serializable(with = Base64ByteArraySerializer::class)
    val receiptMac: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PairingCommitAckV3) return false

        if (!sessionId.contentEquals(other.sessionId)) return false
        if (!transcriptHash.contentEquals(other.transcriptHash)) return false
        if (!receiptMac.contentEquals(other.receiptMac)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sessionId.contentHashCode()
        result = 31 * result + transcriptHash.contentHashCode()
        result = 31 * result + receiptMac.contentHashCode()
        return result
    }
}
