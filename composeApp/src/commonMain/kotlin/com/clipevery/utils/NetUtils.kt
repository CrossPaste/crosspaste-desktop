package com.clipevery.utils

import com.clipevery.dao.sync.HostInfo

expect fun getNetUtils(): NetUtils

interface NetUtils {

    fun getHostInfoList(): List<HostInfo>

    fun getEn0IPAddress(): String?
}
