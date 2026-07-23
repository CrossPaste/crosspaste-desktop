package com.crosspaste.pairing.v3

/**
 * Every security-relevant field of one pairing session, in canonical form.
 *
 * Both roles must construct byte-identical transcripts; the transcript hash is the
 * binding anchor for PAKE key confirmation, identity signatures, and commit MACs.
 * Roles are explicit — fields are never sorted or swapped by role.
 */
data class PairingTranscript(
    val protocolVersion: Int,
    val selectedCiphersuite: String,
    val sessionId: ByteArray,
    val tokenGeneration: Long,
    val initiatorAppInstanceId: String,
    val acceptorAppInstanceId: String,
    val initiatorNonce: ByteArray,
    val acceptorNonce: ByteArray,
    val initiatorSignPublicKey: ByteArray,
    val initiatorCryptPublicKey: ByteArray,
    val acceptorSignPublicKey: ByteArray,
    val acceptorCryptPublicKey: ByteArray,
    val initiatorPakeShare: ByteArray,
    val acceptorPakeShare: ByteArray,
    val intentHash: ByteArray,
    val offerHash: ByteArray,
    val negotiatedCapabilities: List<String>,
) {
    /**
     * Equality is defined as canonical-encoding equality: the length-prefixed
     * encoding is injective, so two transcripts are equal exactly when every
     * field matches — and a future field added to the codec is automatically
     * covered without touching this method.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PairingTranscript) return false
        return PairingTranscriptCodec
            .encodeTranscript(this)
            .contentEquals(PairingTranscriptCodec.encodeTranscript(other))
    }

    override fun hashCode(): Int = PairingTranscriptCodec.encodeTranscript(this).contentHashCode()
}
