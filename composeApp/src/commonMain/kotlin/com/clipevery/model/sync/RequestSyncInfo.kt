package com.clipevery.model.sync

import com.clipevery.model.RequestEndpointInfo
import kotlinx.serialization.Serializable

@Serializable
data class RequestSyncInfo(val requestEndpointInfo: RequestEndpointInfo,
                           val preKeyBundle: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RequestSyncInfo

        if (requestEndpointInfo != other.requestEndpointInfo) return false
        if (!preKeyBundle.contentEquals(other.preKeyBundle)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = requestEndpointInfo.hashCode()
        result = 31 * result + preKeyBundle.contentHashCode()
        return result
    }
}
