package com.crosspaste.platform.macos

import com.crosspaste.platform.macos.api.MacosApi
import com.crosspaste.platform.macos.api.MacosApi.Companion.getString

object MacDeviceUtils {

    fun getComputerName(): String? {
        return getString(MacosApi.INSTANCE.getComputerName())
    }

    fun getHardwareUUID(): String? {
        return getString(MacosApi.INSTANCE.getHardwareUUID())
    }
}
