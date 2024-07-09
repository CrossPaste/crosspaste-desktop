package com.crosspaste.dto.sync

import com.crosspaste.serializer.Base64ByteArraySerializer
import kotlinx.serialization.Serializable

@Serializable
data class DataContent(
    @Serializable(with = Base64ByteArraySerializer::class) val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DataContent) return false

        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        return data.contentHashCode()
    }
}
