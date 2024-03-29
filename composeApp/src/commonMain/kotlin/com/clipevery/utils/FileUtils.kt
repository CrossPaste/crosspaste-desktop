package com.clipevery.utils

import com.clipevery.app.AppFileType
import com.clipevery.presist.FileInfoTree
import com.clipevery.presist.FilesChunk
import io.ktor.utils.io.*
import java.nio.file.Path

interface FileUtils {

    val tempDirectory: Path

    fun formatBytes(bytesSize: Long): String

    fun createRandomFileName(ext: String): String

    fun getExtFromFileName(fileName: String): String?

    fun createClipRelativePath(appInstanceId: String, clipId: Int, fileName: String): String

    fun createClipPath(fileRelativePath: String, isFile: Boolean, appFileType: AppFileType): Path

    fun getFileInfoTree(path: Path): FileInfoTree

    fun getFileSize(path: Path): Long

    fun getFileMd5(path: Path): String

    fun copyPath(src: Path, dest: Path): Boolean

    fun moveFile(src: Path, dest: Path): Boolean

    fun createTempFile(src: Path, name: String): Path?

    fun createTempFile(srcBytes: ByteArray, name: String): Path?

    fun createSymbolicLink(src: Path, name: String): Path?

    fun createEmptyClipFile(path: Path, length: Long): Boolean

    suspend fun writeFilesChunk(filesChunk: FilesChunk, byteReadChannel: ByteReadChannel)

    suspend fun readFilesChunk(filesChunk: FilesChunk, byteWriteChannel: ByteWriteChannel)
}