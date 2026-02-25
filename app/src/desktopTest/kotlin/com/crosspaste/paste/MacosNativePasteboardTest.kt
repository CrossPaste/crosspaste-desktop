package com.crosspaste.paste

import com.crosspaste.platform.macos.api.FileResolverCallback
import com.crosspaste.platform.macos.api.MacosApi
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@EnabledOnOs(OS.MAC)
@EnabledIf("isNotHeadless")
class MacosNativePasteboardTest {

    companion object {
        @JvmStatic
        fun isNotHeadless(): Boolean = !GraphicsEnvironment.isHeadless()

        private lateinit var toolkit: Toolkit

        @JvmStatic
        @BeforeAll
        fun initAwt() {
            toolkit = Toolkit.getDefaultToolkit()
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            // Clear clipboard and drain AWT event queue to prevent the JBR shutdown race
            // where AWT's setBusy JNI call fires after the JVM starts tearing down
            try {
                toolkit.systemClipboard.setContents(StringSelection(""), null)
            } catch (_: Exception) {
            }
            SwingUtilities.invokeAndWait {}
            Thread.sleep(200)
        }
    }

    private val systemClipboard by lazy { toolkit.systemClipboard }

    private fun cleanupClipboard() {
        try {
            systemClipboard.setContents(StringSelection(""), null)
        } catch (_: Exception) {
        }
    }

    private fun pathResolver(paths: List<String>): FileResolverCallback =
        FileResolverCallback { index, buffer, bufferSize ->
            writePath(paths[index], buffer, bufferSize)
        }

    private fun writePath(
        path: String,
        buffer: Pointer,
        bufferSize: Int,
    ): Int {
        val bytes = path.toByteArray(Charsets.UTF_8)
        if (bytes.size + 1 > bufferSize) return -1
        buffer.write(0, bytes, 0, bytes.size)
        buffer.setByte(bytes.size.toLong(), 0)
        return bytes.size
    }

    @Test
    fun `writeFilesToPasteboard returns valid changeCount for single file`() {
        val tempFile = File.createTempFile("native-pb-test", ".txt")
        try {
            tempFile.writeText("test")
            val resolver = pathResolver(listOf(tempFile.absolutePath))
            val changeCount = MacosApi.INSTANCE.writeFilesToPasteboard(1, resolver)
            assertTrue(changeCount >= 0, "Expected valid changeCount, got $changeCount")
        } finally {
            tempFile.delete()
            cleanupClipboard()
        }
    }

    @Test
    fun `writeFilesToPasteboard returns valid changeCount for multiple files`() {
        val tempFiles = (1..3).map { File.createTempFile("native-pb-test-$it", ".txt") }
        try {
            tempFiles.forEach { it.writeText("content") }
            val resolver = pathResolver(tempFiles.map { it.absolutePath })
            val changeCount = MacosApi.INSTANCE.writeFilesToPasteboard(tempFiles.size, resolver)
            assertTrue(changeCount >= 0, "Expected valid changeCount, got $changeCount")
        } finally {
            tempFiles.forEach { it.delete() }
            cleanupClipboard()
        }
    }

    @Test
    fun `writeFilesToPasteboard returns -1 for zero count`() {
        val resolver = FileResolverCallback { _, _, _ -> -1 }
        val changeCount = MacosApi.INSTANCE.writeFilesToPasteboard(0, resolver)
        assertEquals(-1, changeCount, "Expected -1 for zero count")
    }

    @Test
    fun `native pasteboard single file is readable from JVM clipboard`() {
        val tempFile = File.createTempFile("native-pb-read", ".txt")
        try {
            tempFile.writeText("readable content")
            val resolver = pathResolver(listOf(tempFile.absolutePath))
            val changeCount = MacosApi.INSTANCE.writeFilesToPasteboard(1, resolver)
            assertTrue(changeCount >= 0)

            val contents = systemClipboard.getContents(null)
            assertNotNull(contents, "Clipboard contents should not be null")
            assertTrue(
                contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor),
                "Clipboard should support javaFileListFlavor",
            )

            @Suppress("UNCHECKED_CAST")
            val files = contents.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
            assertEquals(1, files.size, "Expected 1 file in clipboard")
            assertEquals(tempFile.absolutePath, files[0].absolutePath)
        } finally {
            tempFile.delete()
            cleanupClipboard()
        }
    }

    @Test
    fun `native pasteboard multiple files are readable from JVM clipboard`() {
        val tempFiles = (1..3).map { File.createTempFile("native-pb-multi-$it", ".txt") }
        try {
            tempFiles.forEach { it.writeText("content") }
            val resolver = pathResolver(tempFiles.map { it.absolutePath })
            val changeCount = MacosApi.INSTANCE.writeFilesToPasteboard(tempFiles.size, resolver)
            assertTrue(changeCount >= 0)

            val contents = systemClipboard.getContents(null)
            assertNotNull(contents)

            @Suppress("UNCHECKED_CAST")
            val files = contents.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
            assertEquals(tempFiles.size, files.size, "Expected ${tempFiles.size} files")
            tempFiles.forEachIndexed { i, expected ->
                assertEquals(expected.absolutePath, files[i].absolutePath)
            }
        } finally {
            tempFiles.forEach { it.delete() }
            cleanupClipboard()
        }
    }

    @Test
    fun `writeFilesToPasteboard sets crosspaste marker`() {
        val tempFile = File.createTempFile("native-pb-marker", ".txt")
        try {
            tempFile.writeText("marker test")
            val resolver = pathResolver(listOf(tempFile.absolutePath))
            val writeChangeCount = MacosApi.INSTANCE.writeFilesToPasteboard(1, resolver)
            assertTrue(writeChangeCount >= 0)

            val remote = IntByReference()
            val isCrossPaste = IntByReference()
            // Pass a stale changeCount so getPasteboardChangeCount inspects the items
            MacosApi.INSTANCE.getPasteboardChangeCount(writeChangeCount - 1, remote, isCrossPaste)
            assertTrue(isCrossPaste.value != 0, "isCrossPaste marker should be set")
        } finally {
            tempFile.delete()
            cleanupClipboard()
        }
    }

    @Test
    fun `data provider is NOT called during write - only on read`() {
        val tempFile = File.createTempFile("native-pb-lazy", ".txt")
        try {
            tempFile.writeText("lazy verification")
            MacosApi.INSTANCE.resetProvideDataCallCount()

            val resolver = pathResolver(listOf(tempFile.absolutePath))
            val changeCount = MacosApi.INSTANCE.writeFilesToPasteboard(1, resolver)
            assertTrue(changeCount >= 0)

            // After write, the data provider should NOT have been called (lazy)
            assertEquals(
                0,
                MacosApi.INSTANCE.getProvideDataCallCount(),
                "provideDataForType should NOT be called during writeFilesToPasteboard (lazy write)",
            )

            // Now read from clipboard — this triggers the NSPasteboardItemDataProvider callback
            val contents = systemClipboard.getContents(null)
            assertNotNull(contents)
            contents.getTransferData(DataFlavor.javaFileListFlavor)

            // After read, the data provider SHOULD have been called
            assertTrue(
                MacosApi.INSTANCE.getProvideDataCallCount() > 0,
                "provideDataForType should be called when clipboard data is read (lazy provision)",
            )
        } finally {
            tempFile.delete()
            cleanupClipboard()
        }
    }
}
