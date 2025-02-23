package com.crosspaste.module

import okio.Path

data class ModuleLoaderConfig(
    val installPath: Path,
    val moduleName: String,
    val moduleItems: List<ModuleItem>,
    val retryNumber: Int = 2,
) {
    fun getModuleItem(moduleItemName: String): ModuleItem? {
        return moduleItems.find { it.moduleItemName == moduleItemName }
    }
}

data class ModuleItem(
    val hosts: List<String>,
    val path: String,
    val moduleItemName: String,
    val downloadFileName: String = path.substringAfterLast("/"),
    val relativePath: List<String>,
    val sha256: String,
) {

    fun getModuleFilePath(installPath: Path): Path {
        var path = installPath
        relativePath.forEach {
            path = path.resolve(it)
        }
        return path
    }

    fun getUrls(): List<String> {
        return hosts.map { host -> "$host$path" }
    }
}
