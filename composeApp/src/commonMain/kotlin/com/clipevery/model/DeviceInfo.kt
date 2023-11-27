package com.clipevery.model

import com.clipevery.platform.Platform

data class DeviceInfo(
    val deviceId: String,
    val appInfo: AppInfo,
    val appHostInfo: AppHostInfo,
    val platform: Platform,
    val state: DeviceState
)
