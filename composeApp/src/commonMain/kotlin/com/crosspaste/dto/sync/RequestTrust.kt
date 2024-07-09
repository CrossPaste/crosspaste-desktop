package com.crosspaste.dto.sync

import com.crosspaste.serializer.Base64ByteArraySerializer
import kotlinx.serialization.Serializable

@Serializable
data class RequestTrust(
    @Serializable(with = Base64ByteArraySerializer::class)
    val identityKey: ByteArray,
    val token: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RequestTrust) return false

        if (!identityKey.contentEquals(other.identityKey)) return false
        if (token != other.token) return false

        return true
    }

    override fun hashCode(): Int {
        var result = identityKey.contentHashCode()
        result = 31 * result + token
        return result
    }
}
