package com.crosspaste.utils

import okio.Path.Companion.toOkioPath
import okio.buffer
import okio.sink
import okio.source
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompressUtilsTest {

    private val compressUtils = getCompressUtils()

    @Test
    fun `zipFile and unzip round-trip preserves file content`() {
        val tempDir = Files.createTempDirectory("compress-test").toFile()
        tempDir.deleteOnExit()

        // Create source file
        val sourceFile = tempDir.resolve("hello.txt")
        sourceFile.writeText("Hello, CrossPaste!")

        // Zip the file
        val zipFile = tempDir.resolve("output.zip")
        zipFile.outputStream().sink().buffer().use { sink ->
            val result = compressUtils.zipFile(sourceFile.toOkioPath(), sink)
            assertTrue(result.isSuccess)
        }

        // Unzip to new directory
        val unzipDir = tempDir.resolve("unzipped")
        unzipDir.mkdirs()
        zipFile.inputStream().source().buffer().use { source ->
            val result = compressUtils.unzip(source, unzipDir.toOkioPath())
            assertTrue(result.isSuccess)
        }

        // Verify content
        val restored = unzipDir.resolve("hello.txt")
        assertTrue(restored.exists())
        assertEquals("Hello, CrossPaste!", restored.readText())
    }

    @Test
    fun `zipDir and unzip round-trip preserves directory structure`() {
        val tempDir = Files.createTempDirectory("compress-dir-test").toFile()
        tempDir.deleteOnExit()

        // Create source directory with nested files
        val sourceDir = tempDir.resolve("source")
        sourceDir.mkdirs()
        sourceDir.resolve("file1.txt").writeText("content1")
        val subDir = sourceDir.resolve("sub")
        subDir.mkdirs()
        subDir.resolve("file2.txt").writeText("content2")

        // Zip the directory
        val zipFile = tempDir.resolve("dir.zip")
        zipFile.outputStream().sink().buffer().use { sink ->
            val result = compressUtils.zipDir(sourceDir.toOkioPath(), sink)
            assertTrue(result.isSuccess)
        }

        // Unzip to new directory
        val unzipDir = tempDir.resolve("restored")
        unzipDir.mkdirs()
        zipFile.inputStream().source().buffer().use { source ->
            val result = compressUtils.unzip(source, unzipDir.toOkioPath())
            assertTrue(result.isSuccess)
        }

        // Verify structure
        assertEquals("content1", unzipDir.resolve("file1.txt").readText())
        assertEquals("content2", unzipDir.resolve("sub").resolve("file2.txt").readText())
    }

    @Test
    fun `zipFile fails when source is a directory`() {
        val tempDir = Files.createTempDirectory("compress-fail-test").toFile()
        tempDir.deleteOnExit()

        val zipFile = tempDir.resolve("output.zip")
        zipFile.outputStream().sink().buffer().use { sink ->
            val result = compressUtils.zipFile(tempDir.toOkioPath(), sink)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun `zipDir fails when source is a file`() {
        val tempDir = Files.createTempDirectory("compress-fail-test2").toFile()
        tempDir.deleteOnExit()
        val file = tempDir.resolve("file.txt")
        file.writeText("data")

        val zipFile = tempDir.resolve("output.zip")
        zipFile.outputStream().sink().buffer().use { sink ->
            val result = compressUtils.zipDir(file.toOkioPath(), sink)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun `unzip rejects zip entry with path traversal`() {
        val tempDir = Files.createTempDirectory("zip-traversal-test").toFile()
        tempDir.deleteOnExit()

        // Create a malicious zip with "../escape.txt" entry
        val zipFile = tempDir.resolve("evil.zip")
        java.util.zip.ZipOutputStream(zipFile.outputStream()).use { zos ->
            val entry = java.util.zip.ZipEntry("../escape.txt")
            zos.putNextEntry(entry)
            zos.write("malicious".toByteArray())
            zos.closeEntry()
        }

        val targetDir = tempDir.resolve("safe")
        targetDir.mkdirs()
        zipFile.inputStream().source().buffer().use { source ->
            val result = compressUtils.unzip(source, targetDir.toOkioPath())
            assertTrue(result.isFailure, "Should reject path traversal zip entries")
        }
    }

    @Test
    fun `zipFile and unzip round-trip preserves binary content`() {
        val tempDir = Files.createTempDirectory("compress-binary-test").toFile()
        tempDir.deleteOnExit()

        // Create binary file
        val binaryData = ByteArray(1024) { (it % 256).toByte() }
        val sourceFile = tempDir.resolve("data.bin")
        sourceFile.writeBytes(binaryData)

        // Zip -> Unzip
        val zipFile = tempDir.resolve("binary.zip")
        zipFile.outputStream().sink().buffer().use { sink ->
            compressUtils.zipFile(sourceFile.toOkioPath(), sink)
        }
        val unzipDir = tempDir.resolve("bin-restored")
        unzipDir.mkdirs()
        zipFile.inputStream().source().buffer().use { source ->
            compressUtils.unzip(source, unzipDir.toOkioPath())
        }

        assertTrue(binaryData.contentEquals(unzipDir.resolve("data.bin").readBytes()))
    }
}
