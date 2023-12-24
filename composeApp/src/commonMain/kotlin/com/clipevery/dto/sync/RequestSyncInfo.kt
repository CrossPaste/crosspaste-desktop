package com.clipevery.dto.sync

import com.clipevery.app.AppInfo
import com.clipevery.endpoint.EndpointInfo
import kotlinx.serialization.Serializable

@Serializable
data class RequestSyncInfo(val appInfo: AppInfo,
                           val endpointInfo: EndpointInfo,
                           val preKeyBundle: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RequestSyncInfo

        if (appInfo != other.appInfo) return false
        if (endpointInfo != other.endpointInfo) return false
        if (!preKeyBundle.contentEquals(other.preKeyBundle)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = appInfo.hashCode()
        result = 31 * result + endpointInfo.hashCode()
        result = 31 * result + preKeyBundle.contentHashCode()
        return result
    }
}
