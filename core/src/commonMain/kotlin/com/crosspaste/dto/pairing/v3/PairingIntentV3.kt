package com.crosspaste.dto.pairing.v3

import com.crosspaste.serializer.Base64ByteArraySerializer
import kotlinx.serialization.Serializable

/**
 * First message of pairing v3, sent by the initiator to the acceptor.
 *
 * The signature proves possession of [initiatorSignPublicKey] but does not make
 * the key trusted. It covers every preceding field via the v3 canonical codec.
 */
@Serializable
data class PairingIntentV3(
    val protocolVersion: Int,
    @Serializable(with = Base64ByteArraySerializer::class)
    val requestId: ByteArray,
    val initiatorAppInstanceId: String,
    val targetAppInstanceId: String,
    val initiatorDisplayName: String,
    @Serializable(with = Base64ByteArraySerializer::class)
    val initiatorSignPublicKey: ByteArray,
    @Serializable(with = Base64ByteArraySerializer::class)
    val initiatorCryptPublicKey: ByteArray,
    @Serializable(with = Base64ByteArraySerializer::class)
    val initiatorNonce: ByteArray,
    val supportedCiphersuites: List<String>,
    @Serializable(with = Base64ByteArraySerializer::class)
    val signature: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PairingIntentV3) return false

        if (protocolVersion != other.protocolVersion) return false
        if (!requestId.contentEquals(other.requestId)) return false
        if (initiatorAppInstanceId != other.initiatorAppInstanceId) return false
        if (targetAppInstanceId != other.targetAppInstanceId) return false
        if (initiatorDisplayName != other.initiatorDisplayName) return false
        if (!initiatorSignPublicKey.contentEquals(other.initiatorSignPublicKey)) return false
        if (!initiatorCryptPublicKey.contentEquals(other.initiatorCryptPublicKey)) return false
        if (!initiatorNonce.contentEquals(other.initiatorNonce)) return false
        if (supportedCiphersuites != other.supportedCiphersuites) return false
        if (!signature.contentEquals(other.signature)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = protocolVersion
        result = 31 * result + requestId.contentHashCode()
        result = 31 * result + initiatorAppInstanceId.hashCode()
        result = 31 * result + targetAppInstanceId.hashCode()
        result = 31 * result + initiatorDisplayName.hashCode()
        result = 31 * result + initiatorSignPublicKey.contentHashCode()
        result = 31 * result + initiatorCryptPublicKey.contentHashCode()
        result = 31 * result + initiatorNonce.contentHashCode()
        result = 31 * result + supportedCiphersuites.hashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }
}
