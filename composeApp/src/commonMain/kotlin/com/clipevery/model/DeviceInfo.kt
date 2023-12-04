package com.clipevery.model

import com.clipevery.platform.Platform
import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(val deviceId: String,
                      val deviceName: String,
                      val platform: Platform,
                      val hostInfoList: List<HostInfo>)
