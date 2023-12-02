package com.clipevery.device

import com.clipevery.model.DeviceInfo

interface DeviceInfoFactory {

    fun createDeviceInfo(): DeviceInfo
}
