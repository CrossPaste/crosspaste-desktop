package com.crosspaste.utils

import com.crosspaste.app.AppFileType
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.FileInfoTree
import com.crosspaste.presist.FileInfoTreeBuilder
import com.crosspaste.presist.FilesChunk
import com.crosspaste.presist.SingleFileInfoTree
import io.ktor.utils.io.*
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

expect fun getFileUtils(): FileUtils

const val B = "B"
const val KB = "KB"
const val MB = "MB"
const val GB = "GB"
const val TB = "TB"

interface FileUtils {

    val fileSystem: FileSystem

    val fileBufferSize: Int

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

    fun createRandomFileName(ext: String? = null): String

    fun getImageExtFromFileName(fileName: String): String? {
        val index = fileName.lastIndexOf(".")
        return if (index != -1) {
            val ext = fileName.substring(index + 1)
            if (canPreviewImage(ext)) {
                ext
            } else {
                null
            }
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

    fun getFileInfoTree(path: Path): FileInfoTree {
        return if (path.isDirectory) {
            getDirFileInfoTree(path)
        } else {
            getSingleFileInfoTree(path)
        }
    }

    private fun getDirFileInfoTree(path: Path): FileInfoTree {
        val builder = FileInfoTreeBuilder()

        fileSystem.list(path).sorted().forEach { childPath ->
            val fileName = childPath.name
            val fileTree = getFileInfoTree(childPath)
            builder.addFileInfoTree(fileName, fileTree)
        }

        return builder.build(path)
    }

    private fun getSingleFileInfoTree(path: Path): FileInfoTree {
        val size = getFileSize(path)
        val hash = getFileHash(path)
        return SingleFileInfoTree(size, hash)
    }

    fun getFileSize(path: Path): Long

    fun getFileHash(path: Path): String {
        val streamingMurmurHash3 = StreamingMurmurHash3(CROSS_PASTE_SEED)
        val buffer = ByteArray(fileBufferSize)

        fileSystem.source(path).buffer().use { bufferedSource ->
            while (true) {
                val bytesRead = bufferedSource.read(buffer, 0, fileBufferSize)
                if (bytesRead == -1) break
                streamingMurmurHash3.update(buffer, 0, bytesRead)
            }
        }

        val (hash1, hash2) = streamingMurmurHash3.finish()
        return buildString(32) {
            appendHex(hash1)
            appendHex(hash2)
        }
    }

    fun existFile(path: Path): Boolean {
        val result =
            runCatching {
                fileSystem.exists(path)
            }
        return result.getOrDefault(false)
    }

    fun deleteFile(path: Path): Result<Unit> =
        runCatching {
            if (path.isDirectory) {
                fileSystem.deleteRecursively(path)
            } else {
                fileSystem.delete(path)
            }
        }

    fun createFile(
        path: Path,
        mustCreate: Boolean = false,
    ): Result<Unit> =
        runCatching {
            fileSystem.write(path, mustCreate = mustCreate) {
                // Create an empty file
            }
        }

    fun createDir(
        path: Path,
        mustCreate: Boolean = false,
    ): Result<Unit> =
        runCatching {
            fileSystem.createDirectories(path, mustCreate = mustCreate)
        }

    fun copyPath(
        src: Path,
        dest: Path,
    ): Result<Unit> =
        runCatching {
            if (fileSystem.metadata(src).isDirectory) {
                copyDirectory(src, dest)
            } else {
                fileSystem.copy(src, dest)
            }
        }

    private fun copyDirectory(
        src: Path,
        dest: Path,
    ) {
        fileSystem.createDirectory(dest)
        fileSystem.list(src).forEach { item ->
            val newSrc = src / item.name
            val newDest = dest / item.name
            if (fileSystem.metadata(newSrc).isDirectory) {
                copyDirectory(newSrc, newDest)
            } else {
                fileSystem.copy(newSrc, newDest)
            }
        }
    }

    fun moveFile(
        src: Path,
        dest: Path,
    ): Result<Unit> =
        runCatching {
            fileSystem.atomicMove(src, dest)
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
