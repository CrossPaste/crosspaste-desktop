package com.crosspaste.paste

import com.crosspaste.paste.plugin.type.DesktopFilesTypePlugin.Companion.FILE_LIST_ID
import com.crosspaste.paste.plugin.type.DesktopHtmlTypePlugin.Companion.HTML_ID
import com.crosspaste.paste.plugin.type.DesktopImageTypePlugin.Companion.IMAGE_JPEG
import com.crosspaste.paste.plugin.type.DesktopImageTypePlugin.Companion.IMAGE_PNG
import com.crosspaste.paste.plugin.type.DesktopRtfTypePlugin.Companion.RTF_ID
import com.crosspaste.paste.plugin.type.DesktopTextTypePlugin.Companion.UNICODE_STRING
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WaylandClipboardSnapshotTest {

    /** Serves the given payloads and records which MIME types were asked for. */
    private class Offer(
        private val payloads: Map<String, ByteArray>,
    ) {
        val reads = mutableListOf<String>()

        val mimeTypes: List<String> get() = payloads.keys.toList()

        fun read(mimeType: String): ByteArray? {
            reads += mimeType
            return payloads[mimeType]
        }
    }

    private fun Transferable.identities(): List<String> = transferDataFlavors.map { it.humanPresentableName }

    private fun Transferable.bytes(flavor: DataFlavor): ByteArray = (getTransferData(flavor) as InputStream).readBytes()

    @Test
    fun `spectacle screenshot maps to the image png flavor`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val offer =
            Offer(
                mapOf(
                    "image/png" to png,
                    "image/jpeg" to byteArrayOf(1),
                    "image/bmp" to byteArrayOf(2),
                    "application/x-qt-image" to byteArrayOf(3),
                ),
            )

        val snapshot = assertNotNull(WaylandClipboardSnapshot.create(offer.mimeTypes, offer::read))

        assertEquals(listOf(IMAGE_PNG), snapshot.identities())
        assertEquals(listOf("image/png"), offer.reads)
        assertContentEquals(png, snapshot.bytes(snapshot.transferDataFlavors[0]))
    }

    @Test
    fun `jpeg is used when no png is offered`() {
        val offer = Offer(mapOf("image/jpeg" to byteArrayOf(1)))
        val snapshot = assertNotNull(WaylandClipboardSnapshot.create(offer.mimeTypes, offer::read))
        assertEquals(listOf(IMAGE_JPEG), snapshot.identities())
    }

    @Test
    fun `text prefers the utf-8 type and is read with the offered spelling`() {
        val offer =
            Offer(
                mapOf(
                    "TEXT" to "latin".toByteArray(Charsets.ISO_8859_1),
                    "text/plain;charset=UTF-8" to "你好".toByteArray(Charsets.UTF_8),
                    "text/plain" to "plain".toByteArray(),
                ),
            )

        val snapshot = assertNotNull(WaylandClipboardSnapshot.create(offer.mimeTypes, offer::read))

        assertEquals(listOf(UNICODE_STRING), snapshot.identities())
        assertEquals(listOf("text/plain;charset=UTF-8"), offer.reads)
        assertEquals("你好", snapshot.getTransferData(DataFlavor.stringFlavor))
    }

    @Test
    fun `legacy STRING is decoded as latin-1`() {
        val offer = Offer(mapOf("STRING" to "café".toByteArray(Charsets.ISO_8859_1)))
        val snapshot = assertNotNull(WaylandClipboardSnapshot.create(offer.mimeTypes, offer::read))
        assertEquals("café", snapshot.getTransferData(DataFlavor.stringFlavor))
    }

    @Test
    fun `html is decoded from its declared charset and exposed as a string`() {
        val html = "<meta charset=\"gbk\"><p>中文</p>"
        val offer =
            Offer(
                mapOf(
                    "text/html" to html.toByteArray(charset("GBK")),
                    "text/plain;charset=utf-8" to "中文".toByteArray(),
                ),
            )

        val snapshot = assertNotNull(WaylandClipboardSnapshot.create(offer.mimeTypes, offer::read))

        assertEquals(listOf(HTML_ID, UNICODE_STRING), snapshot.identities())
        assertEquals(html, snapshot.getTransferData(WaylandClipboardSnapshot.HTML_FLAVOR))
    }

    @Test
    fun `rtf is exposed as a stream under the rtf identity`() {
        val rtf = "{\\rtf1 hi}".toByteArray()
        val offer = Offer(mapOf("text/rtf" to rtf, "text/plain" to "hi".toByteArray()))

        val snapshot = assertNotNull(WaylandClipboardSnapshot.create(offer.mimeTypes, offer::read))

        assertEquals(listOf(RTF_ID, UNICODE_STRING), snapshot.identities())
        assertContentEquals(rtf, snapshot.bytes(WaylandClipboardSnapshot.RTF_FLAVOR))
    }

    @Test
    fun `files from a uri-list become a java file list and suppress the icon image`() {
        val offer =
            Offer(
                mapOf(
                    "text/uri-list" to "# comment\r\nfile:///tmp/a%20b.txt\r\nhttps://example.com/x\r\n".toByteArray(),
                    "image/png" to byteArrayOf(1),
                    "text/plain;charset=utf-8" to "/tmp/a b.txt".toByteArray(),
                ),
            )

        val snapshot = assertNotNull(WaylandClipboardSnapshot.create(offer.mimeTypes, offer::read))

        assertEquals(listOf(FILE_LIST_ID, UNICODE_STRING), snapshot.identities())
        assertEquals(listOf(File("/tmp/a b.txt")), snapshot.getTransferData(DataFlavor.javaFileListFlavor))
        assertFalse("image/png" in offer.reads)
    }

    @Test
    fun `gnome copied files body is parsed when no uri-list is offered`() {
        val offer =
            Offer(mapOf("x-special/gnome-copied-files" to "copy\nfile:///home/u/doc.pdf\n".toByteArray()))

        val snapshot = assertNotNull(WaylandClipboardSnapshot.create(offer.mimeTypes, offer::read))

        assertEquals(listOf(File("/home/u/doc.pdf")), snapshot.getTransferData(DataFlavor.javaFileListFlavor))
    }

    @Test
    fun `a uri-list without local files does not count as files`() {
        val offer =
            Offer(
                mapOf(
                    "text/uri-list" to "https://example.com/\n".toByteArray(),
                    "image/png" to byteArrayOf(1),
                ),
            )

        val snapshot = assertNotNull(WaylandClipboardSnapshot.create(offer.mimeTypes, offer::read))

        assertEquals(listOf(IMAGE_PNG), snapshot.identities())
    }

    @Test
    fun `nothing readable yields no snapshot`() {
        assertNull(WaylandClipboardSnapshot.create(listOf("application/x-qt-image"), { byteArrayOf(1) }))
        assertNull(WaylandClipboardSnapshot.create(listOf("image/png"), { null }))
        assertNull(WaylandClipboardSnapshot.create(emptyList(), { byteArrayOf(1) }))
    }

    @Test
    fun `a failed read leaves only that flavor out`() {
        val offer = Offer(mapOf("text/html" to "<b>x</b>".toByteArray(), "text/plain" to "x".toByteArray()))
        val snapshot =
            assertNotNull(
                WaylandClipboardSnapshot.create(offer.mimeTypes) { mime ->
                    if (mime == "text/html") null else offer.read(mime)
                },
            )
        assertEquals(listOf(UNICODE_STRING), snapshot.identities())
    }

    @Test
    fun `byte payloads can be read more than once`() {
        val offer = Offer(mapOf("image/png" to byteArrayOf(7, 8, 9)))
        val snapshot = assertNotNull(WaylandClipboardSnapshot.create(offer.mimeTypes, offer::read))
        val flavor = snapshot.transferDataFlavors[0]

        assertContentEquals(byteArrayOf(7, 8, 9), snapshot.bytes(flavor))
        assertContentEquals(byteArrayOf(7, 8, 9), snapshot.bytes(flavor))
        assertTrue(snapshot.isDataFlavorSupported(flavor))
    }
}
