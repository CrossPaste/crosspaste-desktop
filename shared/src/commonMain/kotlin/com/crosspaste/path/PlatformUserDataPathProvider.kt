package com.crosspaste.path

import okio.Path

interface PlatformUserDataPathProvider {

    companion object {
        const val CROSSPASTE_DIR_NAME = ".crosspaste"
    }

    fun getUserDefaultStoragePath(): Path
}
