package com.clipevery.dto.sync

import com.clipevery.serializer.Base64ByteArraySerializer
import kotlinx.serialization.Serializable

@Serializable
data class DataContent(
    @Serializable(with = Base64ByteArraySerializer::class) val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DataContent

        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        return data.contentHashCode()
    }
}
