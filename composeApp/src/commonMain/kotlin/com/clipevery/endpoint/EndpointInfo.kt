package com.clipevery.endpoint

import com.clipevery.dao.sync.HostInfo
import com.clipevery.platform.Platform
import kotlinx.serialization.Serializable

@Serializable
data class EndpointInfo(val deviceId: String,
                        val deviceName: String,
                        val platform: Platform,
                        val hostInfoList: List<HostInfo>,
                        val port: Int)

data class ExplicitEndpointInfo(val deviceId: String,
                                val deviceName: String,
                                val platform: Platform,
                                val hostInfo: HostInfo,
                                val port: Int)

