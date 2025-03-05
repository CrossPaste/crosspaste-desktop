package com.crosspaste.utils

import okio.Path
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

actual fun getCompressUtils(): CompressUtils {
    return DesktopCompressUtils
}

object DesktopCompressUtils : CompressUtils {

    override fun zipDir(
        sourceDir: Path,
        targetZipPath: Path,
    ): Result<Unit> {
        return try {
            require(sourceDir.isDirectory) { "Source must be a directory" }

            val targetFile = targetZipPath.toFile()

            ZipOutputStream(BufferedOutputStream(targetFile.outputStream())).use { zipOut ->
                val basePath = sourceDir.toFile().absolutePath
                sourceDir.toFile().walkTopDown().filter { it.isFile }.forEach { file ->
                    val entryPath =
                        file.absolutePath.removePrefix(basePath)
                            .removePrefix(File.separator)
                            .replace(File.separatorChar, '/')

                    val entry = ZipEntry(entryPath)
                    zipOut.putNextEntry(entry)

                    BufferedInputStream(file.inputStream()).use { input ->
                        input.copyTo(zipOut)
                    }

                    zipOut.closeEntry()
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun zipFile(
        sourceFile: Path,
        targetZipPath: Path,
    ): Result<Unit> {
        return try {
            require(!sourceFile.isDirectory) { "Source must be a file, not a directory" }

            ZipOutputStream(BufferedOutputStream(targetZipPath.toFile().outputStream())).use { zipOut ->
                val entry = ZipEntry(sourceFile.name)
                zipOut.putNextEntry(entry)

                BufferedInputStream(sourceFile.toFile().inputStream()).use { input ->
                    input.copyTo(zipOut)
                }

                zipOut.closeEntry()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun unzip(
        zipFile: Path,
        targetDir: Path,
    ): Result<Unit> {
        return try {
            ZipInputStream(BufferedInputStream(zipFile.toFile().inputStream())).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val filePath = targetDir.resolve(entry.name)

                    filePath.parent?.toFile()?.mkdirs()

                    if (!entry.isDirectory) {
                        BufferedOutputStream(filePath.toFile().outputStream()).use { output ->
                            zipIn.copyTo(output)
                        }
                    }

                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
