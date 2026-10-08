package com.crosspaste.utils

import okio.BufferedSink
import okio.BufferedSource
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

actual fun getCompressUtils(): CompressUtils = DesktopCompressUtils

object DesktopCompressUtils : CompressUtils {

    override fun openZip(targetBufferedSink: BufferedSink): ZipWriter =
        DesktopZipWriter(ZipOutputStream(BufferedOutputStream(targetBufferedSink.outputStream())))

    override fun zipDir(
        sourceDir: Path,
        targetBufferedSink: BufferedSink,
    ): Result<Unit> =
        runCatching {
            require(sourceDir.isDirectory) { "Source must be a directory" }
            openZip(targetBufferedSink).use { writer ->
                sourceDir.toFile().listFiles()?.forEach { child ->
                    writer.addPath(child.name, child.toOkioPath())
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
            val canonicalTarget = targetDir.toFile().canonicalPath
            ZipInputStream(
                BufferedInputStream(bufferSource.inputStream()),
            ).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val filePath = targetDir.resolve(entry.name)
                    val canonicalFile = filePath.toFile().canonicalPath
                    require(canonicalFile.startsWith(canonicalTarget)) {
                        "Zip entry outside target dir: ${entry.name}"
                    }

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

private class DesktopZipWriter(
    private val zipOut: ZipOutputStream,
) : ZipWriter {

    override fun close() {
        zipOut.close()
    }

    override fun addPath(
        entryName: String,
        path: Path,
    ) {
        val file = path.toFile()
        if (file.isDirectory) {
            file.walkTopDown().filter { it.isFile }.forEach { child ->
                addFile("$entryName/${child.relativeTo(file).invariantSeparatorsPath}", child)
            }
        } else {
            addFile(entryName, file)
        }
    }

    private fun addFile(
        entryName: String,
        file: File,
    ) {
        zipOut.putNextEntry(ZipEntry(entryName))
        BufferedInputStream(file.inputStream()).use { input ->
            input.copyTo(zipOut)
        }
        zipOut.closeEntry()
    }
}
