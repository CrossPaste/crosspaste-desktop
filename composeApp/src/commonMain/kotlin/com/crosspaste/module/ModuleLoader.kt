package com.crosspaste.module

import com.crosspaste.utils.CodecsUtils
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.Loader
import com.crosspaste.utils.RetryUtils
import com.crosspaste.utils.noOptionParent
import okio.Path

interface ModuleLoader : Loader<ModuleLoaderConfig, Path> {

    val retryUtils: RetryUtils

    val fileUtils: FileUtils

    val codecsUtils: CodecsUtils

    fun verifyModule(
        path: Path,
        sha256: String,
    ): Boolean {
        return codecsUtils.sha256(path) == sha256
    }

    fun installModule(path: Path): Boolean {
        return true
    }

    fun downloadModule(
        url: String,
        path: Path,
    ): Boolean

    override fun load(value: ModuleLoaderConfig): Path? {
        return retryUtils.retry(value.retryNumber) {
            if (!fileUtils.existFile(value.installPath)) {
                fileUtils.createDir(value.installPath.noOptionParent)
                if (!downloadModule(value.url, value.installPath)) {
                    fileUtils.deleteFile(value.installPath)
                    return@retry null
                }
            }

            if (!verifyModule(value.installPath, value.sha256)) {
                fileUtils.deleteFile(value.installPath)
                return@retry null
            }

            if (installModule(value.installPath)) {
                value.installPath
            } else {
                null
            }
        }
    }
}
