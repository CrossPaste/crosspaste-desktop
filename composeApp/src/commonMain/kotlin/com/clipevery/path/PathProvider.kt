package com.clipevery.path

import com.clipevery.config.FileType
import java.nio.file.Path

interface PathProvider {
    fun resolve(fileName: String?, fileType: FileType): Path {
        val path = when (fileType) {
            FileType.USER -> clipUserPath
            FileType.LOG -> clipUserPath.resolve("logs")
            FileType.ENCRYPT -> clipUserPath.resolve("encrypt")
            FileType.DATA -> clipUserPath.resolve("data")
        }

        if (!path.toFile().exists()) {
            path.toFile().mkdirs()
        }

        return fileName?.let {
            path.resolve(fileName)
        } ?: path
    }

    val clipUserPath: Path

}