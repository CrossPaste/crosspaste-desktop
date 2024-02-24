package com.clipevery.utils

import java.nio.file.Path

interface FileUtils {

    fun formatBytes(bytesSize: Long): String

    fun createRandomFileName(ext: String): String

    fun getExtFromFileName(fileName: String): String?

    fun createClipRelativePath(clipId: Int, fileName: String): String

    fun getFileNameFromRelativePath(relativePath: Path): String

    fun createClipPath(fileRelativePath: String, isFile: Boolean, isImage: Boolean = false): Path

    fun getFileSize(path: Path): Long

    fun getFileMd5(path: Path): String
}