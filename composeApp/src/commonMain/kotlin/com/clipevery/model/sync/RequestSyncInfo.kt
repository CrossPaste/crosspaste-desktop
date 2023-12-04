package com.clipevery.model.sync

import com.clipevery.model.AppInfo
import com.clipevery.model.RequestEndpointInfo
import kotlinx.serialization.Serializable

@Serializable
data class RequestSyncInfo(val appInfo: AppInfo,
                           val requestEndpointInfo: RequestEndpointInfo,
                           val preKeyBundle: ByteArray,
                           val salt: Int) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RequestSyncInfo

        if (appInfo != other.appInfo) return false
        if (requestEndpointInfo != other.requestEndpointInfo) return false
        if (!preKeyBundle.contentEquals(other.preKeyBundle)) return false
        if (salt != other.salt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = appInfo.hashCode()
        result = 31 * result + requestEndpointInfo.hashCode()
        result = 31 * result + preKeyBundle.contentHashCode()
        result = 31 * result + salt
        return result
    }

}
