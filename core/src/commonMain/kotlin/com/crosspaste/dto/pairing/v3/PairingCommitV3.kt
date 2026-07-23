package com.crosspaste.dto.pairing.v3

import com.crosspaste.serializer.Base64ByteArraySerializer
import kotlinx.serialization.Serializable

/**
 * Explicit initiator commit after both proofs verified.
 *
 * Receiving a valid commit is what allows the acceptor to persist the initiator's
 * long-term crypt public key. A byte-identical retry is idempotent; a different
 * commit for the same session is rejected.
 */
@Serializable
data class PairingCommitV3(
    @Serializable(with = Base64ByteArraySerializer::class)
    val sessionId: ByteArray,
    @Serializable(with = Base64ByteArraySerializer::class)
    val transcriptHash: ByteArray,
    @Serializable(with = Base64ByteArraySerializer::class)
    val commitMac: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PairingCommitV3) return false

        if (!sessionId.contentEquals(other.sessionId)) return false
        if (!transcriptHash.contentEquals(other.transcriptHash)) return false
        if (!commitMac.contentEquals(other.commitMac)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sessionId.contentHashCode()
        result = 31 * result + transcriptHash.contentHashCode()
        result = 31 * result + commitMac.contentHashCode()
        return result
    }
}
