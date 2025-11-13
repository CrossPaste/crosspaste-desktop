package com.crosspaste.db.sync

import com.crosspaste.net.HostInfoFilter
import kotlinx.serialization.Serializable

@Serializable
data class HostInfo(
    val networkPrefixLength: Short,
    var hostAddress: String
) {
    fun filter(host: String): Boolean {
        return HostInfoFilter(
            hostAddress = hostAddress,
            networkPrefixLength = networkPrefixLength,
        ).filter(host)
    }
}
