package com.crosspaste.os.macos

import com.crosspaste.os.macos.api.MacosApi
import com.crosspaste.os.macos.api.MacosApi.Companion.getString

object MacDeviceUtils {

    fun getComputerName(): String? {
        return getString(MacosApi.INSTANCE.getComputerName())
    }

    fun getHardwareUUID(): String? {
        return getString(MacosApi.INSTANCE.getHardwareUUID())
    }
}
