package com.crosspaste.module

import com.crosspaste.app.AppFileType
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.CodecsUtils
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.Loader
import com.crosspaste.utils.RetryUtils
import okio.Path

interface ModuleLoader : Loader<ModuleLoaderConfig, Path> {

    val retryUtils: RetryUtils

    val fileUtils: FileUtils

    val codecsUtils: CodecsUtils

    val userDataPathProvider: UserDataPathProvider

    /**
     * Verify module by path and sha256
     */
    fun verifyModule(
        path: Path,
        sha256: String,
    ): Boolean {
        return codecsUtils.sha256(path) == sha256
    }

    /**
     * Install module from path to path
     */
    fun installModule(
        downloadPath: Path,
        installPath: Path,
    ): Boolean

    /**
     * Download module from url to path
     */
    fun downloadModule(
        url: String,
        path: Path,
    ): Boolean

    override fun load(value: ModuleLoaderConfig): Path? {
        return retryUtils.retry(value.retryNumber) {
            val downTempPath = userDataPathProvider.resolve(value.downloadFileName, AppFileType.TEMP)

            if (!fileUtils.existFile(downTempPath)) {
                if (!downloadModule(value.url, downTempPath)) {
                    fileUtils.deleteFile(downTempPath)
                    return@retry null
                }
            }

            if (!verifyModule(downTempPath, value.sha256)) {
                fileUtils.deleteFile(downTempPath)
                return@retry null
            }

            if (installModule(downTempPath, value.installPath)) {
                value.getModuleFilePath()
            } else {
                null
            }
        }
    }
}
