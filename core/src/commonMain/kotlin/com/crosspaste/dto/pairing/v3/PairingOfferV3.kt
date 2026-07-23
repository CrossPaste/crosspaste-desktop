package com.crosspaste.dto.pairing.v3

import com.crosspaste.serializer.Base64ByteArraySerializer
import kotlinx.serialization.Serializable

/**
 * Acceptor response to a valid [PairingIntentV3].
 *
 * The acceptor has created the pairing session and displayed the PIN locally.
 * The PIN and its random secret are never part of this message.
 */
@Serializable
data class PairingOfferV3(
    val protocolVersion: Int,
    val selectedCiphersuite: String,
    @Serializable(with = Base64ByteArraySerializer::class)
    val sessionId: ByteArray,
    @Serializable(with = Base64ByteArraySerializer::class)
    val requestHash: ByteArray,
    val tokenGeneration: Long,
    val pinExpiresAt: Long,
    val initiatorAppInstanceId: String,
    val acceptorAppInstanceId: String,
    @Serializable(with = Base64ByteArraySerializer::class)
    val acceptorSignPublicKey: ByteArray,
    @Serializable(with = Base64ByteArraySerializer::class)
    val acceptorCryptPublicKey: ByteArray,
    @Serializable(with = Base64ByteArraySerializer::class)
    val acceptorNonce: ByteArray,
    @Serializable(with = Base64ByteArraySerializer::class)
    val acceptorPakeShare: ByteArray,
    @Serializable(with = Base64ByteArraySerializer::class)
    val signature: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PairingOfferV3) return false

        if (protocolVersion != other.protocolVersion) return false
        if (selectedCiphersuite != other.selectedCiphersuite) return false
        if (!sessionId.contentEquals(other.sessionId)) return false
        if (!requestHash.contentEquals(other.requestHash)) return false
        if (tokenGeneration != other.tokenGeneration) return false
        if (pinExpiresAt != other.pinExpiresAt) return false
        if (initiatorAppInstanceId != other.initiatorAppInstanceId) return false
        if (acceptorAppInstanceId != other.acceptorAppInstanceId) return false
        if (!acceptorSignPublicKey.contentEquals(other.acceptorSignPublicKey)) return false
        if (!acceptorCryptPublicKey.contentEquals(other.acceptorCryptPublicKey)) return false
        if (!acceptorNonce.contentEquals(other.acceptorNonce)) return false
        if (!acceptorPakeShare.contentEquals(other.acceptorPakeShare)) return false
        if (!signature.contentEquals(other.signature)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = protocolVersion
        result = 31 * result + selectedCiphersuite.hashCode()
        result = 31 * result + sessionId.contentHashCode()
        result = 31 * result + requestHash.contentHashCode()
        result = 31 * result + tokenGeneration.hashCode()
        result = 31 * result + pinExpiresAt.hashCode()
        result = 31 * result + initiatorAppInstanceId.hashCode()
        result = 31 * result + acceptorAppInstanceId.hashCode()
        result = 31 * result + acceptorSignPublicKey.contentHashCode()
        result = 31 * result + acceptorCryptPublicKey.contentHashCode()
        result = 31 * result + acceptorNonce.contentHashCode()
        result = 31 * result + acceptorPakeShare.contentHashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }
}
