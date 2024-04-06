package com.clipevery.utils

import com.clipevery.dao.sync.HostInfo

interface NetUtils {

    fun getHostInfoList(): List<HostInfo>

    fun getEn0IPAddress(): String?
}