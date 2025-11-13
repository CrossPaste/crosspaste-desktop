package com.crosspaste.db.sync

import com.crosspaste.net.HostInfoFilter.Companion.createHostInfoFilter
import kotlinx.serialization.Serializable

@Serializable
data class HostInfo(
    val networkPrefixLength: Short,
    var hostAddress: String
) {
    fun filter(host: String): Boolean {
        return createHostInfoFilter(
            hostAddress = hostAddress,
            networkPrefixLength = networkPrefixLength,
        ).filter(host)
    }
}
