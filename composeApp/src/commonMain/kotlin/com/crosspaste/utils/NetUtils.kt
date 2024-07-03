package com.crosspaste.utils

import com.crosspaste.dao.sync.HostInfo

expect fun getNetUtils(): NetUtils

interface NetUtils {

    fun getHostInfoList(hostInfoFilter: (HostInfo) -> Boolean): List<HostInfo>

    fun hostPreFixMatch(
        host1: String,
        host2: String,
        prefixLength: Short,
    ): Boolean

    fun getEn0IPAddress(): String?
}
