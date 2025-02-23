package com.crosspaste.dto.secure

import com.crosspaste.serializer.Base64ByteArraySerializer
import kotlinx.serialization.Serializable

@Serializable
data class PairingRequest(
    @Serializable(with = Base64ByteArraySerializer::class)
    val signPublicKey: ByteArray,
    @Serializable(with = Base64ByteArraySerializer::class)
    val cryptPublicKey: ByteArray,
    val token: Int,
    val timestamp: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PairingRequest) return false

        if (!signPublicKey.contentEquals(other.signPublicKey)) return false
        if (!cryptPublicKey.contentEquals(other.cryptPublicKey)) return false
        if (token != other.token) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = signPublicKey.contentHashCode()
        result = 31 * result + cryptPublicKey.contentHashCode()
        result = 31 * result + token
        result = 31 * result + timestamp.hashCode()
        return result
    }
}
