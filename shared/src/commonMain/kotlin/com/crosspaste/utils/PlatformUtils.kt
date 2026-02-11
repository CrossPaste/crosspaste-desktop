package com.crosspaste.utils

import com.crosspaste.platform.Platform

expect fun getPlatformUtils(): PlatformUtils

interface PlatformUtils {

    val platform: Platform
}
