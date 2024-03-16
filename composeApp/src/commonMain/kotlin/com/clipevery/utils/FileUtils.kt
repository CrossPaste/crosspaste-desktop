package com.clipevery.utils

import com.clipevery.app.AppFileType
import java.nio.file.Path

interface FileUtils {

    fun formatBytes(bytesSize: Long): String

    fun createRandomFileName(ext: String): String

    fun getExtFromFileName(fileName: String): String?

    fun createClipRelativePath(appInstanceId: String, clipId: Int, fileName: String): String

    fun getFileNameFromRelativePath(relativePath: Path): String

    fun createClipPath(fileRelativePath: String, isFile: Boolean, appFileType: AppFileType): Path

    fun getFileSize(path: Path): Long

    fun getFileMd5(path: Path): String

    fun copyFile(src: Path, dest: Path): Boolean

    fun moveFile(src: Path, dest: Path): Boolean

    fun createTempDirectory(): Path
}