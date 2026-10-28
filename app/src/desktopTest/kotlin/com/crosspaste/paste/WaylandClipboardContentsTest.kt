package com.crosspaste.paste

import com.crosspaste.paste.plugin.type.DesktopRtfTypePlugin.Companion.RTF_DATA_FLAVOR
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WaylandClipboardContentsTest {

    private fun transferable(vararg values: Pair<DataFlavor, Any>): DesktopWriteTransferable =
        DesktopWriteTransferable(linkedMapOf(*values))

    private fun Map<String, () -> ByteArray?>.text(mimeType: String): String? =
        this[mimeType]?.invoke()?.decodeToString()

    @Test
    fun `text is offered under every text type plus the origin marker`() {
        val contents = WaylandClipboardContents.create(transferable(DataFlavor.stringFlavor to "héllo"))

        assertEquals(
            listOf(
                "text/plain;charset=utf-8",
                "UTF8_STRING",
                "text/plain",
                "STRING",
                "TEXT",
                PasteDataFlavors.CROSSPASTE_ORIGIN_MIME,
            ),
            contents.keys.toList(),
        )
        assertEquals("héllo", contents.text("text/plain;charset=utf-8"))
        assertEquals("crosspaste", contents.text(PasteDataFlavors.CROSSPASTE_ORIGIN_MIME))
    }

    @Test
    fun `files are offered as a uri list and as gnome copied files ahead of their text`() {
        val files = listOf(File("/tmp/a b.txt"), File("/tmp/c.png"))
        val contents =
            WaylandClipboardContents.create(
                transferable(
                    DataFlavor.javaFileListFlavor to files,
                    DataFlavor.stringFlavor to "a b.txt\nc.png",
                ),
            )

        assertEquals("text/uri-list", contents.keys.first())
        assertEquals("file:///tmp/a%20b.txt\r\nfile:///tmp/c.png\r\n", contents.text("text/uri-list"))
        assertEquals("copy\nfile:///tmp/a%20b.txt\nfile:///tmp/c.png", contents.text("x-special/gnome-copied-files"))
        // What we write reads back as the same files.
        assertEquals(files, WaylandClipboardSnapshot.parseFileUris(contents.text("text/uri-list")!!))
    }

    @Test
    fun `html and a single-use rtf stream can each be served more than once`() {
        val rtf = "{\\rtf1 hi}".encodeToByteArray()
        val contents =
            WaylandClipboardContents.create(
                transferable(
                    DataFlavor.allHtmlFlavor to "<b>hi</b>",
                    RTF_DATA_FLAVOR to ByteArrayInputStream(rtf),
                    DataFlavor.stringFlavor to "hi",
                ),
            )

        assertEquals("<b>hi</b>", contents.text("text/html"))
        assertContentEquals(rtf, contents.getValue("text/rtf").invoke())
        assertContentEquals(rtf, contents.getValue("text/rtf").invoke())
        assertContentEquals(rtf, contents.getValue("application/rtf").invoke())
    }

    @Test
    fun `an image is encoded to png only when a client asks for it`() {
        var loads = 0
        val image = BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB)
        val contents =
            WaylandClipboardContents.create(
                transferable(
                    DataFlavor.imageFlavor to
                        LazyTransferData {
                            loads++
                            image
                        },
                ),
            )

        assertTrue("image/png" in contents)
        assertEquals(0, loads)

        val png = assertNotNull(contents.getValue("image/png").invoke())
        val decoded = ImageIO.read(png.inputStream())
        assertEquals(3, decoded.width)
        assertEquals(2, decoded.height)
        contents.getValue("image/png").invoke()
        assertEquals(1, loads)
    }

    @Test
    fun `nothing to paste yields no offer, not a bare marker`() {
        val contents = WaylandClipboardContents.create(transferable(PasteDataFlavors.URL_FLAVOR to "unused"))

        assertTrue(contents.isEmpty())
        assertFalse(PasteDataFlavors.CROSSPASTE_ORIGIN_MIME in contents)
    }
}
