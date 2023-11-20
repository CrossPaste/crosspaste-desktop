package com.clipevery.path

import com.clipevery.config.FileType
import java.nio.file.Path

interface PathProvider {
    fun resolve(fileName: String, fileType: FileType): Path {
        return when (fileType) {
            FileType.USER -> resolveUser(fileName)
            FileType.APP -> resolveApp(fileName)
            FileType.LOG -> resolveLog(fileName)
        }
    }

    fun resolveUser(fileName: String?): Path

    fun resolveApp(fileName: String?): Path

    fun resolveLog(fileName: String?): Path
}