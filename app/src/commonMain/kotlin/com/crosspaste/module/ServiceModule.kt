package com.crosspaste.module

import okio.Path

interface ServiceModule {

    val moduleId: String

    val desc: String?

    fun getCurrentFilePathList(): List<Path>

    fun getFilePath(taskId: String): Path?

    // This method can only be called when the system has just loaded the module.
    // It determines the current download state of the module based on persisted files.
    fun getModuleInitDownloadState(): ModuleDownloadState

    fun createDownloadTask(id: String): DownloadTask?
}
