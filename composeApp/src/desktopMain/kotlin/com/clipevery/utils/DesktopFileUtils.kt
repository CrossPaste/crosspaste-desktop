package com.clipevery.utils

import com.clipevery.app.AppFileType
import com.clipevery.path.DesktopPathProvider
import com.clipevery.utils.EncryptUtils.md5ByArray
import com.clipevery.utils.EncryptUtils.md5ByString
import com.google.common.hash.Hashing
import com.google.common.io.Files
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.text.DecimalFormat
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

    override fun getPathMd5(path: Path): String {
        return if (path.isDirectory()) {
            getDirMd5(path)
        } else {
            getFileMd5(path)
        }
    }

    private fun getFileMd5(path: Path): String {
        val file: File = path.toFile()
        val byteSource = Files.asByteSource(file)
        val hc = byteSource.hash(Hashing.sha256())
        return hc.toString()
    }

    private fun getDirMd5(path: Path): String {
        path.toFile().listFiles()?.let {
            val md5Array = it.sortedBy { file -> file.name }.map { file ->
                getPathMd5(file.toPath())
            }.toTypedArray()
            if (md5Array.isEmpty()) {
                return md5ByString(path.fileName.toString())
            } else {
                return md5ByArray(md5Array)
            }
        } ?: run {
            return md5ByString(path.fileName.toString())
        }
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
}