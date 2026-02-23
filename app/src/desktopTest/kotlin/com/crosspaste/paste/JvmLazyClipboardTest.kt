package com.crosspaste.paste

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.ClipboardOwner
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@EnabledIf("isNotHeadless")
class JvmLazyClipboardTest {

    companion object {
        @JvmStatic
        fun isNotHeadless(): Boolean = !GraphicsEnvironment.isHeadless()
    }

    private val systemClipboard by lazy { Toolkit.getDefaultToolkit().systemClipboard }

    private val noOpOwner = ClipboardOwner { _, _ -> }

    private fun cleanupClipboard() {
        try {
            systemClipboard.setContents(StringSelection(""), noOpOwner)
        } catch (_: Exception) {
            // best-effort cleanup
        }
    }

    private fun trackingStringTransferable(called: AtomicBoolean): Transferable =
        object : Transferable {
            override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.stringFlavor)

            override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.stringFlavor

            override fun getTransferData(flavor: DataFlavor): Any {
                if (flavor == DataFlavor.stringFlavor) {
                    called.set(true)
                    return "test string"
                }
                throw UnsupportedFlavorException(flavor)
            }
        }

    private fun trackingFileListTransferable(called: AtomicBoolean): Transferable =
        object : Transferable {
            override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)

            override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.javaFileListFlavor

            override fun getTransferData(flavor: DataFlavor): Any {
                if (flavor == DataFlavor.javaFileListFlavor) {
                    called.set(true)
                    return emptyList<File>()
                }
                throw UnsupportedFlavorException(flavor)
            }
        }

    private fun trackingMultiFlavorTransferable(
        advertisedFlavors: Array<DataFlavor>,
        callCount: AtomicInteger,
    ): Transferable =
        object : Transferable {
            override fun getTransferDataFlavors(): Array<DataFlavor> = advertisedFlavors

            override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor in advertisedFlavors

            override fun getTransferData(flavor: DataFlavor): Any {
                if (flavor in advertisedFlavors) {
                    callCount.incrementAndGet()
                    return if (flavor == DataFlavor.stringFlavor) "plain text" else "<b>html</b>"
                }
                throw UnsupportedFlavorException(flavor)
            }
        }

    private fun ownerLostTransferable(): Transferable =
        object : Transferable {
            override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.stringFlavor)

            override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.stringFlavor

            override fun getTransferData(flavor: DataFlavor): Any {
                if (flavor == DataFlavor.stringFlavor) return "original"
                throw UnsupportedFlavorException(flavor)
            }
        }

    @Nested
    @EnabledOnOs(OS.MAC)
    inner class MacosEagerClipboardTests {

        @Test
        fun `setContents triggers immediate getTransferData for string flavor`() {
            val called = AtomicBoolean(false)
            try {
                systemClipboard.setContents(trackingStringTransferable(called), noOpOwner)
                assertTrue(called.get(), "getTransferData should be called eagerly during setContents on macOS")
            } finally {
                cleanupClipboard()
            }
        }

        @Test
        fun `setContents triggers immediate getTransferData for file list flavor`() {
            val called = AtomicBoolean(false)
            try {
                systemClipboard.setContents(trackingFileListTransferable(called), noOpOwner)
                assertTrue(called.get(), "getTransferData should be called eagerly during setContents on macOS")
            } finally {
                cleanupClipboard()
            }
        }

        @Test
        fun `setContents triggers immediate getTransferData for all advertised flavors`() {
            val htmlFlavor = DataFlavor("text/html;class=java.lang.String")
            val advertisedFlavors = arrayOf(DataFlavor.stringFlavor, htmlFlavor)
            val callCount = AtomicInteger(0)
            try {
                systemClipboard.setContents(
                    trackingMultiFlavorTransferable(advertisedFlavors, callCount),
                    noOpOwner,
                )
                assertTrue(
                    callCount.get() >= advertisedFlavors.size,
                    "getTransferData should be called at least once per advertised flavor on macOS, " +
                        "but was called ${callCount.get()} times for ${advertisedFlavors.size} flavors",
                )
            } finally {
                cleanupClipboard()
            }
        }

        @Test
        fun `clipboard data persists after owner lost`() {
            try {
                systemClipboard.setContents(ownerLostTransferable(), noOpOwner)
                systemClipboard.setContents(StringSelection("other"), noOpOwner)
                val contents = systemClipboard.getData(DataFlavor.stringFlavor) as String
                assertEquals("other", contents, "Clipboard should contain the new value after owner change")
            } finally {
                cleanupClipboard()
            }
        }
    }

    @Nested
    @EnabledOnOs(OS.WINDOWS)
    inner class WindowsEagerClipboardTests {

        @Test
        fun `setContents triggers immediate getTransferData for string flavor`() {
            val called = AtomicBoolean(false)
            try {
                systemClipboard.setContents(trackingStringTransferable(called), noOpOwner)
                assertTrue(called.get(), "getTransferData should be called eagerly during setContents on Windows")
            } finally {
                cleanupClipboard()
            }
        }

        @Test
        fun `setContents triggers immediate getTransferData for file list flavor`() {
            val called = AtomicBoolean(false)
            try {
                systemClipboard.setContents(trackingFileListTransferable(called), noOpOwner)
                assertTrue(called.get(), "getTransferData should be called eagerly during setContents on Windows")
            } finally {
                cleanupClipboard()
            }
        }

        @Test
        fun `setContents triggers immediate getTransferData for all advertised flavors`() {
            val htmlFlavor = DataFlavor("text/html;class=java.lang.String")
            val advertisedFlavors = arrayOf(DataFlavor.stringFlavor, htmlFlavor)
            val callCount = AtomicInteger(0)
            try {
                systemClipboard.setContents(
                    trackingMultiFlavorTransferable(advertisedFlavors, callCount),
                    noOpOwner,
                )
                assertTrue(
                    callCount.get() >= advertisedFlavors.size,
                    "getTransferData should be called at least once per advertised flavor on Windows, " +
                        "but was called ${callCount.get()} times for ${advertisedFlavors.size} flavors",
                )
            } finally {
                cleanupClipboard()
            }
        }

        @Test
        fun `clipboard data persists after owner lost`() {
            try {
                systemClipboard.setContents(ownerLostTransferable(), noOpOwner)
                systemClipboard.setContents(StringSelection("other"), noOpOwner)
                val contents = systemClipboard.getData(DataFlavor.stringFlavor) as String
                assertEquals("other", contents, "Clipboard should contain the new value after owner change")
            } finally {
                cleanupClipboard()
            }
        }
    }

    @Nested
    @EnabledOnOs(OS.LINUX)
    inner class LinuxLazyClipboardTests {

        @Test
        fun `setContents does not trigger immediate getTransferData for string flavor`() {
            val called = AtomicBoolean(false)
            try {
                systemClipboard.setContents(trackingStringTransferable(called), noOpOwner)
                assertFalse(
                    called.get(),
                    "getTransferData should NOT be called during setContents on Linux (lazy clipboard)",
                )
            } finally {
                cleanupClipboard()
            }
        }

        @Test
        fun `setContents does not trigger immediate getTransferData for file list flavor`() {
            val called = AtomicBoolean(false)
            try {
                systemClipboard.setContents(trackingFileListTransferable(called), noOpOwner)
                assertFalse(
                    called.get(),
                    "getTransferData should NOT be called during setContents on Linux (lazy clipboard)",
                )
            } finally {
                cleanupClipboard()
            }
        }

        @Test
        fun `setContents does not trigger getTransferData for any advertised flavor`() {
            val htmlFlavor = DataFlavor("text/html;class=java.lang.String")
            val advertisedFlavors = arrayOf(DataFlavor.stringFlavor, htmlFlavor)
            val callCount = AtomicInteger(0)
            try {
                systemClipboard.setContents(
                    trackingMultiFlavorTransferable(advertisedFlavors, callCount),
                    noOpOwner,
                )
                assertEquals(
                    0,
                    callCount.get(),
                    "getTransferData should NOT be called during setContents on Linux, " +
                        "but was called ${callCount.get()} times",
                )
            } finally {
                cleanupClipboard()
            }
        }

        @Test
        fun `getTransferData is called lazily on paste request`() {
            val called = AtomicBoolean(false)
            try {
                systemClipboard.setContents(trackingStringTransferable(called), noOpOwner)
                assertFalse(called.get(), "getTransferData should not be called yet")
                // Trigger a paste request by reading from clipboard
                systemClipboard.getData(DataFlavor.stringFlavor)
                assertTrue(called.get(), "getTransferData should be called on paste request")
            } finally {
                cleanupClipboard()
            }
        }
    }
}
