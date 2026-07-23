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
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PairingTranscript) return false

        if (protocolVersion != other.protocolVersion) return false
        if (selectedCiphersuite != other.selectedCiphersuite) return false
        if (!sessionId.contentEquals(other.sessionId)) return false
        if (tokenGeneration != other.tokenGeneration) return false
        if (initiatorAppInstanceId != other.initiatorAppInstanceId) return false
        if (acceptorAppInstanceId != other.acceptorAppInstanceId) return false
        if (!initiatorNonce.contentEquals(other.initiatorNonce)) return false
        if (!acceptorNonce.contentEquals(other.acceptorNonce)) return false
        if (!initiatorSignPublicKey.contentEquals(other.initiatorSignPublicKey)) return false
        if (!initiatorCryptPublicKey.contentEquals(other.initiatorCryptPublicKey)) return false
        if (!acceptorSignPublicKey.contentEquals(other.acceptorSignPublicKey)) return false
        if (!acceptorCryptPublicKey.contentEquals(other.acceptorCryptPublicKey)) return false
        if (!initiatorPakeShare.contentEquals(other.initiatorPakeShare)) return false
        if (!acceptorPakeShare.contentEquals(other.acceptorPakeShare)) return false
        if (!intentHash.contentEquals(other.intentHash)) return false
        if (!offerHash.contentEquals(other.offerHash)) return false
        if (negotiatedCapabilities != other.negotiatedCapabilities) return false

        return true
    }

    override fun hashCode(): Int {
        var result = protocolVersion
        result = 31 * result + selectedCiphersuite.hashCode()
        result = 31 * result + sessionId.contentHashCode()
        result = 31 * result + tokenGeneration.hashCode()
        result = 31 * result + initiatorAppInstanceId.hashCode()
        result = 31 * result + acceptorAppInstanceId.hashCode()
        result = 31 * result + initiatorNonce.contentHashCode()
        result = 31 * result + acceptorNonce.contentHashCode()
        result = 31 * result + initiatorSignPublicKey.contentHashCode()
        result = 31 * result + initiatorCryptPublicKey.contentHashCode()
        result = 31 * result + acceptorSignPublicKey.contentHashCode()
        result = 31 * result + acceptorCryptPublicKey.contentHashCode()
        result = 31 * result + initiatorPakeShare.contentHashCode()
        result = 31 * result + acceptorPakeShare.contentHashCode()
        result = 31 * result + intentHash.contentHashCode()
        result = 31 * result + offerHash.contentHashCode()
        result = 31 * result + negotiatedCapabilities.hashCode()
        return result
    }
}
