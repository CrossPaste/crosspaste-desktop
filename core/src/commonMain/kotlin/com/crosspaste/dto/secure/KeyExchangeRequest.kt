package com.crosspaste.dto.secure

import com.crosspaste.serializer.Base64ByteArraySerializer
import kotlinx.serialization.Serializable

@Serializable
data class KeyExchangeRequest(
    @Serializable(with = Base64ByteArraySerializer::class)
    val signPublicKey: ByteArray,
    @Serializable(with = Base64ByteArraySerializer::class)
    val cryptPublicKey: ByteArray,
    val timestamp: Long,
    @Serializable(with = Base64ByteArraySerializer::class)
    val signature: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyExchangeRequest) return false

        if (!signPublicKey.contentEquals(other.signPublicKey)) return false
        if (!cryptPublicKey.contentEquals(other.cryptPublicKey)) return false
        if (timestamp != other.timestamp) return false
        if (!signature.contentEquals(other.signature)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = signPublicKey.contentHashCode()
        result = 31 * result + cryptPublicKey.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }
}
