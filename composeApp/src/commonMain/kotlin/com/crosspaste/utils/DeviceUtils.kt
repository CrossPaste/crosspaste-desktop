package com.crosspaste.utils

expect fun getDeviceUtils(): DeviceUtils

interface DeviceUtils {

    fun createAppInstanceId(): String

    fun getDeviceId(): String

    fun getDeviceName(): String
}
