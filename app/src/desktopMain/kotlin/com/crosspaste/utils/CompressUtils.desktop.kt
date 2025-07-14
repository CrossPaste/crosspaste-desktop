package com.crosspaste.utils

import okio.BufferedSink
import okio.BufferedSource
import okio.Path
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

actual fun getCompressUtils(): CompressUtils = DesktopCompressUtils

object DesktopCompressUtils : CompressUtils {

    override fun zipDir(
        sourceDir: Path,
        targetBufferedSink: BufferedSink,
    ): Result<Unit> =
        runCatching {
            require(sourceDir.isDirectory) { "Source must be a directory" }

            ZipOutputStream(
                BufferedOutputStream(targetBufferedSink.outputStream()),
            ).use { zipOut ->
                val basePath = sourceDir.toFile().absolutePath
                sourceDir.toFile().walkTopDown().filter { it.isFile }.forEach { file ->
                    val entryPath =
                        file.absolutePath
                            .removePrefix(basePath)
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
        }

    override fun zipFile(
        sourceFile: Path,
        targetBufferedSink: BufferedSink,
    ): Result<Unit> =
        runCatching {
            require(!sourceFile.isDirectory) { "Source must be a file, not a directory" }

            ZipOutputStream(
                BufferedOutputStream(targetBufferedSink.outputStream()),
            ).use { zipOut ->
                val entry = ZipEntry(sourceFile.name)
                zipOut.putNextEntry(entry)

                BufferedInputStream(sourceFile.toFile().inputStream()).use { input ->
                    input.copyTo(zipOut)
                }

                zipOut.closeEntry()
            }
        }

    override fun unzip(
        bufferSource: BufferedSource,
        targetDir: Path,
    ): Result<Unit> =
        runCatching {
            ZipInputStream(
                BufferedInputStream(bufferSource.inputStream()),
            ).use { zipIn ->
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
        }
}
