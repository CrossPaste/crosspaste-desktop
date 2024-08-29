package com.crosspaste.utils

import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileUtilsTest {

    companion object {
        @TempDir
        lateinit var nioTempFolder: File
    }

    private lateinit var fileUtils: FileUtils

    lateinit var tempDir: Path

    @BeforeAll
    fun setup() {
        fileUtils = getFileUtils()
        tempDir = nioTempFolder.toOkioPath()
    }

    @Test
    fun testExistFile() {
        val file = tempDir / "test.txt"
        fileUtils.createFile(file)
        assertTrue(fileUtils.existFile(file))
        assertFalse(fileUtils.existFile(tempDir / "nonexistent.txt"))
    }

    @Test
    fun testDeleteFile() {
        val file = tempDir / "toDelete.txt"
        fileUtils.createFile(file)
        assertTrue(fileUtils.existFile(file))
        val result = fileUtils.deleteFile(file)
        assertTrue(result.isSuccess)
        assertFalse(fileUtils.existFile(file))
    }

    @Test
    fun testCreateFile() {
        val file = tempDir / "newFile.txt"
        val result = fileUtils.createFile(file)
        assertTrue(result.isSuccess)
        assertTrue(fileUtils.existFile(file))
        assertEquals(0L, file.toFile().length())
    }

    @Test
    fun testCreateDir() {
        val dir = tempDir / "newDir"
        val result = fileUtils.createDir(dir)
        assertTrue(result.isSuccess)
        assertTrue(fileUtils.existFile(dir))
        assertTrue(dir.isDirectory)
    }

    @Test
    fun testCopyPath() {
        val sourceFile = tempDir / "source.txt"
        fileUtils.createFile(sourceFile)
        // We can't directly write to the file using okio.Path, so we'll use Java's File API
        File(sourceFile.toString()).writeText("Test content")
        val destFile = tempDir / "dest.txt"

        val result = fileUtils.copyPath(sourceFile, destFile)
        assertTrue(result.isSuccess)
        assertTrue(fileUtils.existFile(destFile))
        assertEquals(sourceFile.toFile().readText(), destFile.toFile().readText())
    }

    @Test
    fun testCopyDirectory() {
        val sourceDir = tempDir / "sourceDir"
        fileUtils.createDir(sourceDir)
        (sourceDir / "file1.txt").toFile().writeText("Content 1")
        fileUtils.createDir(sourceDir / "subDir")
        (sourceDir / "subDir" / "file2.txt").toFile().writeText("Content 2")

        val destDir = tempDir / "destDir"
        val result = fileUtils.copyPath(sourceDir, destDir)

        assertTrue(result.isSuccess)
        assertTrue(fileUtils.existFile(destDir))
        assertTrue(fileUtils.existFile(destDir / "file1.txt"))
        assertTrue(fileUtils.existFile(destDir / "subDir" / "file2.txt"))
        assertEquals("Content 1", (destDir / "file1.txt").toFile().readText())
        assertEquals("Content 2", (destDir / "subDir" / "file2.txt").toFile().readText())
    }

    @Test
    fun testMoveFile() {
        val sourceFile = tempDir / "toMove.txt"
        sourceFile.toFile().writeText("Move me")
        val destFile = tempDir / "moved.txt"

        val result = fileUtils.moveFile(sourceFile, destFile)
        assertTrue(result.isSuccess)
        assertFalse(fileUtils.existFile(sourceFile))
        assertTrue(fileUtils.existFile(destFile))
        assertEquals("Move me", destFile.toFile().readText())
    }

    @AfterAll
    fun cleanup() {
        tempDir.toFile().deleteRecursively()
    }
}
