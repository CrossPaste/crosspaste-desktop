package com.clipevery.path

import com.clipevery.config.FileType
import java.nio.file.Path

interface PathProvider {
    fun resolve(fileName: String?, fileType: FileType): Path {
        val path = when (fileType) {
            FileType.USER -> clipUserPath
            FileType.LOG -> clipLogPath.resolve("logs")
            FileType.ENCRYPT -> clipEncryptPath.resolve("encrypt")
            FileType.DATA -> clipDataPath.resolve("data")
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