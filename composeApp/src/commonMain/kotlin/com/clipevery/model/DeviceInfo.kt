package com.clipevery.model

import com.clipevery.platform.Platform

data class DeviceInfo(val deviceId: String,
                      val deviceName: String,
                      val platform: Platform,
                      val hostInfoList: List<HostInfo>)
