package com.crosspaste.platform.macos

import com.crosspaste.platform.macos.api.MacosApi
import com.crosspaste.platform.macos.api.MacosApi.Companion.getString

object MacDeviceUtils {

    fun getComputerName(): String? = getString(MacosApi.INSTANCE.getComputerName())

    fun getHardwareUUID(): String? = getString(MacosApi.INSTANCE.getHardwareUUID())
}
