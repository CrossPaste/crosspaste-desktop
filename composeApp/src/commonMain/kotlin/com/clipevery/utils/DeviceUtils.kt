package com.clipevery.utils

expect fun getDeviceUtils(): DeviceUtils

interface DeviceUtils {

    fun createAppInstanceId(): String
}
