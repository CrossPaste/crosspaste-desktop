package com.clipevery.utils

import com.clipevery.app.AppFileType
import com.clipevery.path.DesktopPathProvider
import com.google.common.hash.Hashing
import com.google.common.io.Files
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.text.DecimalFormat
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.pathString


object DesktopFileUtils: FileUtils {

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

    override fun createClipRelativePath(appInstanceId: String, clipId: Int, fileName: String): String {
        val dateYYYYMMDD = DateUtils.getYYYYMMDD()
        return Paths.get(appInstanceId, dateYYYYMMDD, clipId.toString(), fileName).pathString
    }

    override fun createClipPath(fileRelativePath: String, isFile: Boolean, appFileType: AppFileType): Path {
        val basePath = DesktopPathProvider.resolve(appFileType = appFileType)
        return DesktopPathProvider.resolve(basePath, fileRelativePath, isFile = isFile)
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

    override fun copyFile(src: Path, dest: Path): Boolean {
        return try {
            Files.copy(src.toFile(), dest.toFile())
            true
        } catch (e: Exception) {
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
}