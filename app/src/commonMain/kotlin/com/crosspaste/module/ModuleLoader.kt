package com.crosspaste.module

import com.crosspaste.app.AppFileType
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.CodecsUtils
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.Loader
import com.crosspaste.utils.RetryUtils
import okio.Path

interface ModuleLoader : Loader<ModuleLoaderConfig, Boolean> {

    val retryUtils: RetryUtils

    val fileUtils: FileUtils

    val codecsUtils: CodecsUtils

    val userDataPathProvider: UserDataPathProvider

    /**
     * Verify module by path and sha256
     */
    fun verifyInstall(
        path: Path,
        sha256: String,
    ): Boolean = codecsUtils.sha256(path) == sha256

    /**
     * Install module from path to path
     */
    fun installModule(
        fileName: String,
        downloadPath: Path,
        installPath: Path,
    ): Boolean

    /**
     * Download module from url to path
     */
    suspend fun downloadModule(
        url: String,
        path: Path,
    ): Boolean

    fun makeInstalled(installPath: Path) {
        fileUtils.createFile(installPath.resolve(".success"))
    }

    fun installed(installPath: Path): Boolean = fileUtils.existFile(installPath.resolve(".success"))

    override suspend fun load(value: ModuleLoaderConfig): Boolean {
        val installPath = value.installPath

        if (!installed(installPath)) {
            for (moduleItem in value.moduleItems) {
                val urls = moduleItem.getUrls()
                val installResult: Boolean? =
                    retryUtils.suspendRetry(value.retryNumber) {
                        val downTempPath =
                            userDataPathProvider.resolve(
                                moduleItem.downloadFileName,
                                AppFileType.TEMP,
                            )
                        if (!fileUtils.existFile(downTempPath)) {
                            if (!downloadModule(urls[it], downTempPath)) {
                                fileUtils.deleteFile(downTempPath)
                                return@suspendRetry null
                            }
                        }

                        if (!verifyInstall(downTempPath, moduleItem.sha256)) {
                            fileUtils.deleteFile(downTempPath)
                            return@suspendRetry null
                        }

                        if (installModule(moduleItem.downloadFileName, downTempPath, installPath)) {
                            return@suspendRetry true
                        } else {
                            return@suspendRetry null
                        }
                    }

                if (installResult != true) {
                    return false
                }
            }
            makeInstalled(installPath)
        }

        if (installed(installPath)) {
            for (moduleItem in value.moduleItems) {
                val downTempPath =
                    userDataPathProvider.resolve(moduleItem.downloadFileName, AppFileType.TEMP)
                fileUtils.deleteFile(downTempPath)
            }
            return true
        } else {
            return false
        }
    }
}
