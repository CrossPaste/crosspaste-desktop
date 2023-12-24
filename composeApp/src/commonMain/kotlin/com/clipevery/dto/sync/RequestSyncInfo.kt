package com.clipevery.dto.sync

import com.clipevery.app.AppInfo
import com.clipevery.endpoint.EndpointInfo
import com.clipevery.serializer.PreKeyBundleSerializer
import kotlinx.serialization.Serializable
import org.signal.libsignal.protocol.state.PreKeyBundle

@Serializable
data class RequestSyncInfo(val appInfo: AppInfo,
                           val endpointInfo: EndpointInfo,
                           @Serializable(with = PreKeyBundleSerializer::class) val preKeyBundle: PreKeyBundle) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RequestSyncInfo

        if (appInfo != other.appInfo) return false
        if (endpointInfo != other.endpointInfo) return false
        if (preKeyBundle != other.preKeyBundle) return false

        return true
    }

    override fun hashCode(): Int {
        var result = appInfo.hashCode()
        result = 31 * result + endpointInfo.hashCode()
        result = 31 * result + preKeyBundle.hashCode()
        return result
    }
}
