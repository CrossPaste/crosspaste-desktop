package com.crosspaste.utils

import com.crosspaste.platform.Platform
import okio.Path

expect fun getPlatformUtils(): PlatformUtils

interface PlatformUtils {

    val platform: Platform

    fun getSystemDownloadDir(): Path

    /**
     * Maps an absolute path stored on this device to where that location is now.
     * Platforms whose app storage can move after a path is stored override this.
     */
    fun relocateStoredPath(path: Path): Path = path
}
