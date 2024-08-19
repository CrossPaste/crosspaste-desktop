package com.crosspaste.module

import okio.Path

data class ModuleLoaderConfig(
    val url: String,
    val downloadFileName: String = url.substringAfterLast("/"),
    val installPath: Path,
    val relativePath: List<String>,
    val sha256: String,
    val retryNumber: Int = 2,
) {

    fun getModuleFilePath(): Path {
        var path = installPath
        relativePath.forEach {
            path = path.resolve(it)
        }
        return path
    }
}
