package com.crosspaste.utils

import com.crosspaste.platform.Platform
import okio.Path

expect fun getPlatformUtils(): PlatformUtils

interface PlatformUtils {

    val platform: Platform

    fun getSystemDownloadDir(): Path
}
