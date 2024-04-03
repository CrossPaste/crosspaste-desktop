package com.clipevery.utils

import com.clipevery.app.AppFileType
import com.clipevery.path.DesktopPathProvider
import com.clipevery.presist.FileInfoTree
import com.clipevery.presist.FileInfoTreeBuilder
import com.clipevery.presist.FilesChunk
import com.clipevery.presist.SingleFileInfoTree
import com.google.common.hash.Hashing
import com.google.common.io.Files
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.*
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Path
import java.nio.file.Paths
import java.text.DecimalFormat
import java.time.LocalDateTime
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString


object DesktopFileUtils: FileUtils {

    private val logger = KotlinLogging.logger {}

    override val tempDirectory: Path = java.nio.file.Files.createTempDirectory("clipevery")

    private val units = arrayOf("B", "KB", "MB", "GB", "TB")
    private val decimalFormat = DecimalFormat("#,##0.#")

    init {
        tempDirectory.toFile().deleteOnExit()
    }

    override fun formatBytes(bytesSize: Long): String {
        if (bytesSize < 1024) return "$bytesSize B"
        var value = bytesSize.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.size - 1) {
            value /= 1024
            unitIndex++
        }
        return "${decimalFormat.format(value)} ${units[unitIndex]}"
    }

    override fun createRandomFileName(ext: String): String {
        return "${UUID.randomUUID()}.$ext"
    }

    override fun getExtFromFileName(fileName: String): String? {
        val index = fileName.lastIndexOf(".")
        return if (index != -1) {
            fileName.substring(index + 1)
        } else {
            null
        }
    }

    override fun createClipRelativePath(appInstanceId: String,
                                        date: LocalDateTime,
                                        clipId: Long,
                                        fileName: String): String {
        val dateYYYYMMDD = DateUtils.getYYYYMMDD(date)
        return Paths.get(appInstanceId, dateYYYYMMDD, clipId.toString(), fileName).pathString
    }

    override fun createClipPath(fileRelativePath: String, isFile: Boolean, appFileType: AppFileType): Path {
        val basePath = DesktopPathProvider.resolve(appFileType = appFileType)
        return DesktopPathProvider.resolve(basePath, fileRelativePath, isFile = isFile)
    }

    override fun getFileInfoTree(path: Path): FileInfoTree {
        return if (path.isDirectory()) {
            getDirFileInfoTree(path)
        } else {
            getSingleFileInfoTree(path)
        }
    }

    private fun getDirFileInfoTree(path: Path): FileInfoTree {
        val builder = FileInfoTreeBuilder()
        path.toFile().listFiles()?.let {
            for (file in it.sortedBy { file -> file.name }) {
                val fileTree = getFileInfoTree(file.toPath())
                builder.addFileInfoTree(file.name, fileTree)
            }
        }
        return builder.build(path)
    }

    private fun getSingleFileInfoTree(path: Path): FileInfoTree {
        val size = getFileSize(path)
        val md5 = getFileMd5(path)
        return SingleFileInfoTree(size, md5)
    }

    override fun getFileSize(path: Path): Long {
        return path.toFile().length()
    }

    override fun getFileMd5(path: Path): String {
        val file: File = path.toFile()
        val byteSource = Files.asByteSource(file)
        val hc = byteSource.hash(Hashing.sha256())
        return hc.toString()
    }

    override fun copyPath(src: Path, dest: Path): Boolean {
        return if (src.isDirectory()) {
            copyDir(src, dest)
        } else {
            copyFile(src, dest)
        }
    }

    private fun copyFile(src: Path, dest: Path): Boolean {
        return try {
            Files.copy(src.toFile(), dest.toFile())
            true
        } catch (e: Exception) {
            logger.warn(e) { "Failed to copy file: $src to $dest" }
            false
        }
    }

    private fun copyDir(src: Path, dest: Path): Boolean {
        val newDirFile = dest.toFile()
        return if (newDirFile.mkdirs()) {
            src.toFile().listFiles()?.forEach {
                if(!copyPath(it.toPath(), dest.resolve(it.name))) {
                    return false
                }
            }
            true
        } else {
            logger.warn { "Failed to create directory: $newDirFile" }
            false
        }

    }

    override fun moveFile(src: Path, dest: Path): Boolean {
        return try {
            Files.move(src.toFile(), dest.toFile())
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun createTempFile(src: Path, name: String): Path? {
        val tempFile = tempDirectory.resolve(name)
        return if (copyFile(src, tempFile)) {
            tempFile
        } else {
            null
        }
    }

    override fun createTempFile(srcBytes: ByteArray, name: String): Path? {
        val tempFile = tempDirectory.resolve(name)
        return try {
            Files.write(srcBytes, tempFile.toFile())
            tempFile
        } catch (e: Exception) {
            null
        }
    }

    override fun createSymbolicLink(src: Path, name: String): Path? {
        try {
            val path = tempDirectory.resolve(name)
            if (path.exists()) {
                path.toFile().delete()
            }
            java.nio.file.Files.createSymbolicLink(tempDirectory.resolve(name), src)
            return path
        } catch (e: Exception) {
            return null
        }
    }

    override fun createEmptyClipFile(path: Path, length: Long): Boolean {
        try {
            RandomAccessFile(path.toFile(), "rw").use { file ->
                file.setLength(length)
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }

    override suspend fun writeFilesChunk(filesChunk: FilesChunk, byteReadChannel: ByteReadChannel) {
        filesChunk.fileChunks.forEach { fileChunk ->
            val file = fileChunk.path.toFile()
            val offset = fileChunk.offset
            val size = fileChunk.size
            val buffer = ByteArray(8192)
            RandomAccessFile(file, "rw").use { randomAccessFile ->
                randomAccessFile.seek(offset)
                var remaining = size
                while (remaining > 0) {
                    val toRead = minOf(buffer.size, remaining.toInt())
                    val readSize = byteReadChannel.readAvailable(buffer, 0, toRead)
                    if (readSize == -1) {
                        break
                    }
                    randomAccessFile.write(buffer, 0, readSize)
                    remaining -= readSize
                }
            }
        }
    }

    override suspend fun readFilesChunk(filesChunk: FilesChunk, byteWriteChannel: ByteWriteChannel) {
        filesChunk.fileChunks.forEach { fileChunk ->
            val file = fileChunk.path.toFile()
            val offset = fileChunk.offset
            val size = fileChunk.size
            RandomAccessFile(file, "r").use { randomAccessFile ->
                randomAccessFile.seek(offset)
                val buffer = ByteArray(8192)
                var remaining = size
                while (remaining > 0) {
                    val toRead = minOf(buffer.size, remaining.toInt())
                    val readSize = randomAccessFile.read(buffer, 0, toRead)
                    if (readSize == -1) {
                        break
                    }
                    byteWriteChannel.writeFully(buffer, 0, readSize)
                    remaining -= readSize
                }
            }
        }
    }
}