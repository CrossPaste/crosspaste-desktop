package com.clipevery.dto.sync

import com.clipevery.serializer.Base64MimeByteArraySerializer
import kotlinx.serialization.Serializable

@Serializable
data class ExchangePreKey(
    @Serializable(with = Base64MimeByteArraySerializer::class) val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ExchangePreKey

        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        return data.contentHashCode()
    }
}
