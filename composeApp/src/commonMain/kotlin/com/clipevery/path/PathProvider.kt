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
            AppFileType.IMAGE -> clipDataPath.resolve("images")
            AppFileType.VIDEO -> clipUserPath.resolve("videos")
            AppFileType.FILE -> clipUserPath.resolve("files")
        }

        autoCreateDir(path)

        return fileName?.let {
            path.resolve(fileName)
        } ?: path
    }

    fun resolve(basePath: Path,
                path: String,
                autoCreate: Boolean = true,
                isFile: Boolean = false): Path {
        val newPath = basePath.resolve(path)
        if (isFile) {
            autoCreateDir(newPath.parent)
        } else {
            autoCreateDir(newPath)
        }
        return newPath
    }

    private fun autoCreateDir(path: Path) {
        if (!path.toFile().exists()) {
            path.toFile().mkdirs()
        }
    }

    val clipUserPath: Path

    val clipLogPath: Path get() = clipUserPath

    val clipEncryptPath get() = clipUserPath

    val clipDataPath get() = clipUserPath
}