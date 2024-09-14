package com.crosspaste.utils

import com.crosspaste.app.AppFileType
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.FileInfoTree
import com.crosspaste.presist.FilesChunk
import io.ktor.utils.io.*
import okio.FileSystem
import okio.Path

expect fun getFileUtils(): FileUtils

const val B = "B"
const val KB = "KB"
const val MB = "MB"
const val GB = "GB"
const val TB = "TB"

interface FileUtils {

    val dateUtils: DateUtils

    val separator: String

    fun formatBytes(bytesSize: Long): String

    fun bytesSize(
        size: Long,
        unit: String = MB,
    ): Long {
        return when (unit) {
            KB -> size * 1024
            MB -> size * 1024 * 1024
            GB -> size * 1024 * 1024 * 1024
            TB -> size * 1024 * 1024 * 1024 * 1024
            else -> size
        }
    }

    fun canPreviewImage(ext: String): Boolean

    fun createRandomFileName(ext: String): String

    fun getExtFromFileName(fileName: String): String? {
        val index = fileName.lastIndexOf(".")
        return if (index != -1) {
            fileName.substring(index + 1)
        } else {
            null
        }
    }

    fun createPasteRelativePath(
        pasteCoordinate: PasteCoordinate,
        fileName: String,
    ): String

    fun createPastePath(
        fileRelativePath: String,
        isFile: Boolean,
        appFileType: AppFileType,
        userDataPathProvider: UserDataPathProvider,
    ): Path {
        val basePath = userDataPathProvider.resolve(appFileType = appFileType)
        return userDataPathProvider.resolve(basePath, fileRelativePath, isFile = isFile)
    }

    fun getFileInfoTree(path: Path): FileInfoTree

    fun getFileSize(path: Path): Long

    fun getFileHash(path: Path): String

    fun existFile(path: Path): Boolean {
        return FileSystem.SYSTEM.exists(path)
    }

    fun deleteFile(path: Path): Result<Unit> =
        runCatching {
            FileSystem.SYSTEM.delete(path)
        }

    fun createFile(
        path: Path,
        mustCreate: Boolean = false,
    ): Result<Unit> =
        runCatching {
            FileSystem.SYSTEM.write(path, mustCreate = mustCreate) {
                // Create an empty file
            }
        }

    fun createDir(
        path: Path,
        mustCreate: Boolean = false,
    ): Result<Unit> =
        runCatching {
            FileSystem.SYSTEM.createDirectories(path, mustCreate = mustCreate)
        }

    fun copyPath(
        src: Path,
        dest: Path,
    ): Result<Unit> =
        runCatching {
            if (FileSystem.SYSTEM.metadata(src).isDirectory) {
                copyDirectory(src, dest)
            } else {
                FileSystem.SYSTEM.copy(src, dest)
            }
        }

    private fun copyDirectory(
        src: Path,
        dest: Path,
    ) {
        FileSystem.SYSTEM.createDirectory(dest)
        FileSystem.SYSTEM.list(src).forEach { item ->
            val newSrc = src / item.name
            val newDest = dest / item.name
            if (FileSystem.SYSTEM.metadata(newSrc).isDirectory) {
                copyDirectory(newSrc, newDest)
            } else {
                FileSystem.SYSTEM.copy(newSrc, newDest)
            }
        }
    }

    fun moveFile(
        src: Path,
        dest: Path,
    ): Result<Unit> =
        runCatching {
            FileSystem.SYSTEM.atomicMove(src, dest)
        }

    fun createEmptyPasteFile(
        path: Path,
        length: Long,
    ): Result<Unit>

    suspend fun writeFile(
        path: Path,
        byteReadChannel: ByteReadChannel,
    )

    suspend fun writeFilesChunk(
        filesChunk: FilesChunk,
        byteReadChannel: ByteReadChannel,
    )

    suspend fun readFile(
        path: Path,
        byteWriteChannel: ByteWriteChannel,
    )

    suspend fun readFilesChunk(
        filesChunk: FilesChunk,
        byteWriteChannel: ByteWriteChannel,
    )
}
