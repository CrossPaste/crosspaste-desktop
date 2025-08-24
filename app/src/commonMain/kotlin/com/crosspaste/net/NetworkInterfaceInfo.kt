package com.crosspaste.net

import com.crosspaste.db.sync.HostInfo

data class NetworkInterfaceInfo(
    val name: String,
    val networkPrefixLength: Short,
    val hostAddress: String,
) {
    fun toHostInfo(): HostInfo =
        HostInfo(
            networkPrefixLength = networkPrefixLength,
            hostAddress = hostAddress,
        )

    override fun toString(): String = "$name - $hostAddress/$networkPrefixLength"
}
