package com.crosspaste.path

import okio.Path

interface PlatformUserDataPathProvider {

    fun getUserDefaultStoragePath(): Path
}
