package com.crosspaste.utils

import com.crosspaste.presist.FileInfoTree
import com.crosspaste.presist.FileInfoTreeBuilder
import com.crosspaste.presist.FilesChunk
import com.crosspaste.presist.SingleFileInfoTree
import com.google.common.hash.Hashing
import com.google.common.io.Files
import io.ktor.utils.io.*
import kotlinx.datetime.LocalDateTime
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Paths
import java.text.DecimalFormat
import java.util.UUID
import kotlin.io.path.pathString

actual fun getFileUtils(): FileUtils {
    return DesktopFileUtils
}

object DesktopFileUtils : FileUtils {

    override val dateUtils = getDateUtils()

    override val separator: String = File.separator

    private val units = arrayOf(B, KB, MB, GB, TB)
    private val decimalFormat = DecimalFormat("###0.#")

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

    override fun createPasteRelativePath(
        appInstanceId: String,
        date: LocalDateTime,
        pasteId: Long,
        fileName: String,
    ): String {
        val dateYYYYMMDD = dateUtils.getYYYYMMDD(date)
        return Paths.get(appInstanceId, dateYYYYMMDD, pasteId.toString(), fileName).pathString
    }

    override fun getFileInfoTree(path: Path): FileInfoTree {
        return if (path.isDirectory) {
            getDirFileInfoTree(path)
        } else {
            getSingleFileInfoTree(path)
        }
    }

    private fun getDirFileInfoTree(path: Path): FileInfoTree {
        val builder = FileInfoTreeBuilder()
        path.toFile().listFiles()?.let {
            for (file in it.sortedBy { file -> file.name }) {
                val fileTree = getFileInfoTree(file.toOkioPath())
                builder.addFileInfoTree(file.name, fileTree)
            }
        }
        return builder.build(path)
    }

    private fun getSingleFileInfoTree(path: Path): FileInfoTree {
        val size = getFileSize(path)
        val hash = getFileHash(path)
        return SingleFileInfoTree(size, hash)
    }

    override fun getFileSize(path: Path): Long {
        return path.toFile().length()
    }

    override fun getFileHash(path: Path): String {
        val file: File = path.toFile()
        val byteSource = Files.asByteSource(file)
        val hc = byteSource.hash(Hashing.goodFastHash(128))
        return hc.toString()
    }

    override fun createEmptyPasteFile(
        path: Path,
        length: Long,
    ): Result<Unit> =
        runCatching {
            RandomAccessFile(path.toFile(), "rw").use { file ->
                file.setLength(length)
            }
        }

    override suspend fun writeFile(
        path: Path,
        byteReadChannel: ByteReadChannel,
    ) {
        val buffer = ByteArray(8192 * 10)
        path.toFile().outputStream().use { outputStream ->
            while (true) {
                val readSize = byteReadChannel.readAvailable(buffer)
                if (readSize == -1) {
                    break
                }
                outputStream.write(buffer, 0, readSize)
            }
        }
    }

    override suspend fun writeFilesChunk(
        filesChunk: FilesChunk,
        byteReadChannel: ByteReadChannel,
    ) {
        val buffer = ByteArray(8192 * 10)
        filesChunk.fileChunks.forEach { fileChunk ->
            val file = fileChunk.path.toFile()
            val offset = fileChunk.offset
            val size = fileChunk.size
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

    override suspend fun readFilesChunk(
        filesChunk: FilesChunk,
        byteWriteChannel: ByteWriteChannel,
    ) {
        val buffer = ByteArray(8192 * 10)
        filesChunk.fileChunks.forEach { fileChunk ->
            val file = fileChunk.path.toFile()
            val offset = fileChunk.offset
            val size = fileChunk.size
            RandomAccessFile(file, "r").use { randomAccessFile ->
                randomAccessFile.seek(offset)
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

    override suspend fun readFile(
        path: Path,
        byteWriteChannel: ByteWriteChannel,
    ) {
        val buffer = ByteArray(8192 * 10)
        path.toFile().inputStream().use { inputStream ->
            while (true) {
                val readSize = inputStream.read(buffer)
                if (readSize == -1) {
                    break
                }
                byteWriteChannel.writeFully(buffer, 0, readSize)
            }
        }
    }
}
