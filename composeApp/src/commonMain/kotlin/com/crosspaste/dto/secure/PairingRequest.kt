package com.crosspaste.dto.secure

import com.crosspaste.serializer.Base64ByteArraySerializer
import kotlinx.serialization.Serializable

@Serializable
data class PairingRequest(
    @Serializable(with = Base64ByteArraySerializer::class)
    val identityKey: ByteArray,
    val token: Int,
    val timestamp: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PairingRequest

        if (!identityKey.contentEquals(other.identityKey)) return false
        if (token != other.token) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = identityKey.contentHashCode()
        result = 31 * result + token
        result = 31 * result + timestamp.hashCode()
        return result
    }
}
