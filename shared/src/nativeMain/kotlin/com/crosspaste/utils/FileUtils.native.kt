package com.crosspaste.utils

import com.crosspaste.presist.FilesChunk
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

actual fun getFileUtils(): FileUtils = NativeFileUtils

object NativeFileUtils : FileUtils {

    override val fileSystem: FileSystem = FileSystem.SYSTEM

    override val fileBufferSize: Int = 8192 * 10

    override fun createEmptyPasteFile(
        path: Path,
        length: Long,
    ): Result<Unit> =
        runCatching {
            FileSystem.SYSTEM.sink(path).buffer().use { sink ->
                val emptyChunk = ByteArray(fileBufferSize)

                var remaining = length
                while (remaining > 0) {
                    val writeSize = minOf(remaining, fileBufferSize.toLong()).toInt()
                    sink.write(emptyChunk, 0, writeSize)
                    remaining -= writeSize
                }
            }
        }

    override suspend fun writeFile(
        path: Path,
        byteReadChannel: ByteReadChannel,
    ) {
        val buffer = ByteArray(fileBufferSize)
        fileSystem.write(path) {
            while (true) {
                val readSize = byteReadChannel.readAvailable(buffer)
                if (readSize == -1) {
                    break
                }
                this.write(buffer, 0, readSize)
            }
        }
    }

    override suspend fun writeFilesChunk(
        filesChunk: FilesChunk,
        byteReadChannel: ByteReadChannel,
    ) {
        val buffer = ByteArray(fileBufferSize)
        filesChunk.fileChunks.forEach { fileChunk ->
            val file = fileChunk.path
            var offset = fileChunk.offset
            val size = fileChunk.size
            fileSystem.openReadWrite(file).use { fileHandle ->
                var remaining = size
                while (remaining > 0) {
                    val toRead = minOf(buffer.size, remaining.toInt())
                    val readSize = byteReadChannel.readAvailable(buffer, 0, toRead)
                    if (readSize == -1) {
                        break
                    }
                    fileHandle.write(offset, buffer, 0, readSize)
                    offset += readSize
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
        fileSystem.source(path).buffer().use { source ->
            while (true) {
                val readSize = source.read(buffer)
                if (readSize == -1) {
                    break
                }
                byteWriteChannel.writeFully(buffer, 0, readSize)
            }
        }
    }

    override suspend fun readFilesChunk(
        filesChunk: FilesChunk,
        byteWriteChannel: ByteWriteChannel,
    ) {
        val buffer = ByteArray(fileBufferSize)
        filesChunk.fileChunks.forEach { fileChunk ->
            val file = fileChunk.path
            var offset = fileChunk.offset
            val size = fileChunk.size

            fileSystem.openReadWrite(file).use { fileHandle ->
                var remaining = size
                while (remaining > 0) {
                    val toRead = minOf(buffer.size, remaining.toInt())
                    val readSize = fileHandle.read(offset, buffer, 0, toRead)
                    if (readSize == -1) {
                        break
                    }
                    byteWriteChannel.writeFully(buffer, 0, readSize)
                    offset += readSize
                    remaining -= readSize
                }
            }
        }
    }
}
