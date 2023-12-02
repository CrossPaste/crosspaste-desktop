package com.clipevery.model

import com.clipevery.platform.Platform

data class EndpointInfo(val deviceId: String,
                        val deviceName: String,
                        val platform: Platform,
                        val hostInfo: HostInfo,
                        val port: Int)
