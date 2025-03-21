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
    ): Boolean {
        return codecsUtils.sha256(path) == sha256
    }

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
    fun downloadModule(
        url: String,
        path: Path,
    ): Boolean

    fun makeInstalled(installPath: Path) {
        fileUtils.createFile(installPath.resolve(".success"))
    }

    fun installed(installPath: Path): Boolean {
        return fileUtils.existFile(installPath.resolve(".success"))
    }

    override suspend fun load(value: ModuleLoaderConfig): Boolean {
        val installPath = value.installPath

        if (!installed(installPath)) {
            for (moduleItem in value.moduleItems) {
                val urls = moduleItem.getUrls()
                val installResult: Boolean? =
                    retryUtils.retry(value.retryNumber) {
                        val downTempPath =
                            userDataPathProvider.resolve(
                                moduleItem.downloadFileName,
                                AppFileType.TEMP,
                            )
                        if (!fileUtils.existFile(downTempPath)) {
                            if (!downloadModule(urls[it], downTempPath)) {
                                fileUtils.deleteFile(downTempPath)
                                return@retry null
                            }
                        }

                        if (!verifyInstall(downTempPath, moduleItem.sha256)) {
                            fileUtils.deleteFile(downTempPath)
                            return@retry null
                        }

                        if (installModule(moduleItem.downloadFileName, downTempPath, installPath)) {
                            return@retry true
                        } else {
                            return@retry null
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
