package com.clipevery.path

import com.clipevery.app.AppFileType
import java.nio.file.Path

interface PathProvider {
    fun resolve(fileName: String? = null, appFileType: AppFileType): Path {
        val path = when (appFileType) {
            AppFileType.USER -> clipUserPath
            AppFileType.LOG -> clipLogPath.resolve("logs")
            AppFileType.ENCRYPT -> clipEncryptPath.resolve("encrypt")
            AppFileType.DATA -> clipDataPath.resolve("data")
        }

        if (!path.toFile().exists()) {
            path.toFile().mkdirs()
        }

        return fileName?.let {
            path.resolve(fileName)
        } ?: path
    }

    val clipUserPath: Path

    val clipLogPath: Path get() = clipUserPath

    val clipEncryptPath get() = clipUserPath

    val clipDataPath get() = clipUserPath
}