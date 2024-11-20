package com.crosspaste.dto.secure

import com.crosspaste.serializer.Base64ByteArraySerializer
import kotlinx.serialization.Serializable

@Serializable
data class TrustRequest(
    val pairingRequest: PairingRequest,
    @Serializable(with = Base64ByteArraySerializer::class)
    val signature: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrustRequest) return false

        if (pairingRequest != other.pairingRequest) return false
        if (!signature.contentEquals(other.signature)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pairingRequest.hashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }
}
