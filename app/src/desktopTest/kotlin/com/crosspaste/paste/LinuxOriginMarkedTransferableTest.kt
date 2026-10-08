package com.crosspaste.paste

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinuxOriginMarkedTransferableTest {

    private val delegate =
        object : Transferable {
            override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.stringFlavor)

            override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = flavor == DataFlavor.stringFlavor

            override fun getTransferData(flavor: DataFlavor?): Any = "hello"
        }

    @Test
    fun `adds the origin flavor after the delegate's flavors`() {
        val marked = LinuxOriginMarkedTransferable(delegate)

        assertContentEquals(
            arrayOf(DataFlavor.stringFlavor, PasteDataFlavors.CROSSPASTE_ORIGIN_FLAVOR),
            marked.transferDataFlavors,
        )
        assertTrue(marked.isDataFlavorSupported(PasteDataFlavors.CROSSPASTE_ORIGIN_FLAVOR))
        assertEquals("hello", marked.getTransferData(DataFlavor.stringFlavor))
    }

    @Test
    fun `origin flavor is a stream whose x11 target is the bare mime type`() {
        val marked = LinuxOriginMarkedTransferable(delegate)

        val first = (marked.getTransferData(PasteDataFlavors.CROSSPASTE_ORIGIN_FLAVOR) as InputStream).readBytes()
        val second = (marked.getTransferData(PasteDataFlavors.CROSSPASTE_ORIGIN_FLAVOR) as InputStream).readBytes()
        assertContentEquals(first, second)
        assertTrue(first.isNotEmpty())
        // AWT exports stream flavors as their MIME type, which is what the
        // compositor bridges into the Wayland offer and the monitor looks for.
        assertEquals(
            PasteDataFlavors.CROSSPASTE_ORIGIN_MIME,
            PasteDataFlavors.CROSSPASTE_ORIGIN_FLAVOR.primaryType + "/" +
                PasteDataFlavors.CROSSPASTE_ORIGIN_FLAVOR.subType,
        )
        assertTrue(PasteDataFlavors.CROSSPASTE_ORIGIN_FLAVOR.isRepresentationClassInputStream)
    }
}
