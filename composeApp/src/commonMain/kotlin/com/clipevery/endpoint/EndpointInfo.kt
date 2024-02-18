package com.clipevery.endpoint

import com.clipevery.dao.sync.HostInfo
import com.clipevery.platform.Platform
import com.clipevery.utils.JsonUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class EndpointInfo(val deviceId: String,
                        val deviceName: String,
                        val platform: Platform,
                        val hostInfoList: List<HostInfo>,
                        val port: Int) {
    override fun toString(): String {
        return JsonUtils.JSON.encodeToString(this)
    }
}
