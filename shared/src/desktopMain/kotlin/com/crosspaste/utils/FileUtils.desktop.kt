package com.crosspaste.utils

import com.crosspaste.presist.FilesChunk
import io.ktor.utils.io.*
import okio.FileSystem
import okio.Path
import java.io.RandomAccessFile

actual fun getFileUtils(): FileUtils = DesktopFileUtils

object DesktopFileUtils : FileUtils {

    override val fileSystem: FileSystem = FileSystem.SYSTEM

    override val fileBufferSize: Int = 8192 * 10

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
        val buffer = ByteArray(fileBufferSize)
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
        val buffer = ByteArray(fileBufferSize)
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
        val buffer = ByteArray(fileBufferSize)
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
        val buffer = ByteArray(fileBufferSize)
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
