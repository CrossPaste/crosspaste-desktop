package com.crosspaste.utils

import com.crosspaste.platform.Platform
import okio.Path

expect fun getPlatformUtils(): PlatformUtils

interface PlatformUtils {

    val platform: Platform

    fun getSystemDownloadDir(): Path

    /**
     * Root of the app's own storage when the OS may move it between launches, so
     * paths under it are stored relative to it (see [com.crosspaste.path.encodeStoredPath]).
     * Null where stored absolute paths stay valid.
     */
    fun getRelocatableAppHome(): Path? = null
}
