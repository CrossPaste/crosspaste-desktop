package com.clipevery.dto.model

import com.clipevery.app.AppInfo
import com.clipevery.endpoint.EndpointInfo
import kotlinx.serialization.Serializable

@Serializable
data class RequestSyncInfo(val appInfo: AppInfo,
                           val endpointInfo: EndpointInfo,
                           val preKeyBundle: ByteArray,
                           val token: Int) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RequestSyncInfo

        if (appInfo != other.appInfo) return false
        if (endpointInfo != other.endpointInfo) return false
        if (!preKeyBundle.contentEquals(other.preKeyBundle)) return false
        if (token != other.token) return false

        return true
    }

    override fun hashCode(): Int {
        var result = appInfo.hashCode()
        result = 31 * result + endpointInfo.hashCode()
        result = 31 * result + preKeyBundle.contentHashCode()
        result = 31 * result + token
        return result
    }

}
