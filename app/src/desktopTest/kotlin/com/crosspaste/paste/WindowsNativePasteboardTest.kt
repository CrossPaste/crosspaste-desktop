package com.crosspaste.paste

import com.crosspaste.platform.windows.WindowsLazyClipboard
import com.crosspaste.platform.windows.api.User32
import com.sun.jna.Pointer
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

@EnabledOnOs(OS.WINDOWS)
@EnabledIf("isNotHeadless")
class WindowsNativePasteboardTest {

    companion object {
        @JvmStatic
        fun isNotHeadless(): Boolean = !GraphicsEnvironment.isHeadless()

        private lateinit var toolkit: Toolkit
        private lateinit var clipboard: WindowsLazyClipboard

        @JvmStatic
        @BeforeAll
        fun initAwt() {
            toolkit = Toolkit.getDefaultToolkit()
            clipboard = WindowsLazyClipboard()
            clipboard.start()
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            clipboard.close()
            try {
                toolkit.systemClipboard.setContents(StringSelection(""), null)
            } catch (_: Exception) {
            }
            SwingUtilities.invokeAndWait {}
            Thread.sleep(200)
        }
    }

    private val systemClipboard by lazy { toolkit.systemClipboard }

    /**
     * Use native API to clear clipboard instead of JVM's setContents(StringSelection("")).
     * JVM's setContents makes the JVM the clipboard owner, which causes stale cached
     * transferable to be returned on subsequent getContents() calls even after native code
     * has taken over the clipboard.
     */
    private fun cleanupClipboard() {
        val user32 = User32.INSTANCE
        if (user32.OpenClipboard(null)) {
            user32.EmptyClipboard()
            user32.CloseClipboard()
        }
    }

    /**
     * After native clipboard write, flush the AWT event queue so the JVM processes
     * WM_DESTROYCLIPBOARD and clears its cached owner transferable.
     */
    private fun awaitClipboardReady() {
        SwingUtilities.invokeAndWait {}
        Thread.sleep(50)
    }

    private fun pathResolver(paths: List<String>): (Int, Pointer, Int) -> Int =
        { index, buffer, bufferSize ->
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
    fun `writeFilesToClipboard returns valid sequence number for single file`() {
        val tempFile = File.createTempFile("native-cb-test", ".txt")
        try {
            tempFile.writeText("test")
            val resolver = pathResolver(listOf(tempFile.absolutePath))
            val seqNum = clipboard.writeFilesToClipboard(1, resolver)
            assertTrue(seqNum >= 0, "Expected valid sequence number, got $seqNum")
        } finally {
            tempFile.delete()
            cleanupClipboard()
        }
    }

    @Test
    fun `writeFilesToClipboard returns valid sequence number for multiple files`() {
        val tempFiles = (1..3).map { File.createTempFile("native-cb-test-$it", ".txt") }
        try {
            tempFiles.forEach { it.writeText("content") }
            val resolver = pathResolver(tempFiles.map { it.absolutePath })
            val seqNum = clipboard.writeFilesToClipboard(tempFiles.size, resolver)
            assertTrue(seqNum >= 0, "Expected valid sequence number, got $seqNum")
        } finally {
            tempFiles.forEach { it.delete() }
            cleanupClipboard()
        }
    }

    @Test
    fun `writeFilesToClipboard returns -1 for zero count`() {
        val resolver: (Int, Pointer, Int) -> Int = { _, _, _ -> -1 }
        val seqNum = clipboard.writeFilesToClipboard(0, resolver)
        assertEquals(-1, seqNum, "Expected -1 for zero count")
    }

    @Test
    fun `native clipboard single file is readable from JVM clipboard`() {
        val tempFile = File.createTempFile("native-cb-read", ".txt")
        try {
            tempFile.writeText("readable content")
            val resolver = pathResolver(listOf(tempFile.absolutePath))
            val seqNum = clipboard.writeFilesToClipboard(1, resolver)
            assertTrue(seqNum >= 0)
            awaitClipboardReady()

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
    fun `native clipboard multiple files are readable from JVM clipboard`() {
        val tempFiles = (1..3).map { File.createTempFile("native-cb-multi-$it", ".txt") }
        try {
            tempFiles.forEach { it.writeText("content") }
            val resolver = pathResolver(tempFiles.map { it.absolutePath })
            val seqNum = clipboard.writeFilesToClipboard(tempFiles.size, resolver)
            assertTrue(seqNum >= 0)
            awaitClipboardReady()

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
    fun `writeFilesToClipboard sets CrossPaste marker`() {
        val tempFile = File.createTempFile("native-cb-marker", ".txt")
        try {
            tempFile.writeText("marker test")
            val resolver = pathResolver(listOf(tempFile.absolutePath))
            val seqNum = clipboard.writeFilesToClipboard(1, resolver)
            assertTrue(seqNum >= 0)

            val user32 = User32.INSTANCE
            val crossPasteFormatId = user32.RegisterClipboardFormatA("CrossPaste")
            assertTrue(crossPasteFormatId > 0, "CrossPaste format should be registered")

            assertTrue(user32.OpenClipboard(null), "Should be able to open clipboard")
            try {
                assertTrue(
                    user32.IsClipboardFormatAvailable(crossPasteFormatId),
                    "CrossPaste marker format should be available on clipboard",
                )
            } finally {
                user32.CloseClipboard()
            }
        } finally {
            tempFile.delete()
            cleanupClipboard()
        }
    }

    @Test
    fun `data provider is NOT called during write - only on read`() {
        val tempFile = File.createTempFile("native-cb-lazy", ".txt")
        try {
            tempFile.writeText("lazy verification")
            clipboard.resetProvideDataCallCount()

            val resolver = pathResolver(listOf(tempFile.absolutePath))
            val seqNum = clipboard.writeFilesToClipboard(1, resolver)
            assertTrue(seqNum >= 0)

            // After write, the data provider should NOT have been called (delayed rendering)
            assertEquals(
                0,
                clipboard.provideDataCallCount,
                "WM_RENDERFORMAT should NOT be triggered during writeFilesToClipboard (lazy write)",
            )

            awaitClipboardReady()

            // Now read from clipboard — this triggers WM_RENDERFORMAT
            val contents = systemClipboard.getContents(null)
            assertNotNull(contents)
            contents.getTransferData(DataFlavor.javaFileListFlavor)

            // After read, the data provider SHOULD have been called
            assertTrue(
                clipboard.provideDataCallCount > 0,
                "WM_RENDERFORMAT should be triggered when clipboard data is read (lazy provision)",
            )
        } finally {
            tempFile.delete()
            cleanupClipboard()
        }
    }
}
