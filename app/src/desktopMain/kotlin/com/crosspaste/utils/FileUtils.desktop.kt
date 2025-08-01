package com.crosspaste.utils

import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.presist.FilesChunk
import io.ktor.utils.io.*
import okio.FileSystem
import okio.Path
import java.io.RandomAccessFile
import java.nio.file.Paths
import java.text.DecimalFormat
import java.util.UUID
import kotlin.io.path.pathString

actual fun getFileUtils(): FileUtils = DesktopFileUtils

object DesktopFileUtils : FileUtils {

    private val dateUtils = getDateUtils()

    override val fileSystem: FileSystem = FileSystem.SYSTEM

    override val fileBufferSize: Int = 8192 * 10

    private val units = arrayOf(B, KB, MB, GB, TB)
    private val decimalFormat = DecimalFormat("###0.#")

    private val canPreviewImageMap =
        setOf(
            "png",
            "jpg",
            "jpeg",
            "gif",
            "bmp",
            "webp",
            "heic",
            "heif",
            "tiff",
            "svg",
        )

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

    override fun canPreviewImage(ext: String): Boolean = canPreviewImageMap.contains(ext.lowercase())

    override fun createRandomFileName(ext: String?): String {
        val uuid = UUID.randomUUID().toString()
        return if (ext != null) {
            "$uuid.$ext"
        } else {
            uuid
        }
    }

    override fun createPasteRelativePath(
        pasteCoordinate: PasteCoordinate,
        fileName: String,
    ): String {
        val dateYYYYMMDD =
            dateUtils.getYMD(
                dateUtils.epochMillisecondsToLocalDateTime(pasteCoordinate.createTime),
            )
        return Paths
            .get(
                pasteCoordinate.appInstanceId,
                dateYYYYMMDD,
                pasteCoordinate.id.toString(),
                fileName,
            ).pathString
    }

    override fun getFileSize(path: Path): Long = path.toFile().length()

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
