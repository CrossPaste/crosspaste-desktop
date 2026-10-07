package com.crosspaste.paste

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PrefetchedTransferableTest {

    private val stringFlavor = DataFlavor.stringFlavor
    private val streamFlavor = DataFlavor("text/plain;class=java.io.InputStream")
    private val readerFlavor = DataFlavor("text/plain;class=java.io.Reader")

    /** A drop source that, like AWT drop data, can no longer be read once the drop is over. */
    private class DropSource(
        private val values: Map<DataFlavor, () -> Any?>,
        private val flavors: Array<DataFlavor>? = null,
    ) : Transferable {
        var dropOver = false
        val reads = mutableListOf<DataFlavor>()

        override fun getTransferDataFlavors(): Array<DataFlavor> = flavors ?: values.keys.toTypedArray()

        override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = flavor in values

        override fun getTransferData(flavor: DataFlavor?): Any? {
            check(!dropOver) { "No drop current" }
            reads += flavor!!
            return values.getValue(flavor)()
        }
    }

    @Test
    fun `data stays readable after the drop is over`() {
        val source = DropSource(mapOf(stringFlavor to { "hello" }))
        val prefetched = PrefetchedTransferable.of(source) { true }
        source.dropOver = true

        assertEquals("hello", prefetched.getTransferData(stringFlavor))
    }

    @Test
    fun `lists every source flavor but reads only the selected ones`() {
        val source = DropSource(mapOf(stringFlavor to { "hello" }, readerFlavor to { "reader".reader() }))
        val prefetched = PrefetchedTransferable.of(source) { it == stringFlavor }

        assertContentEquals(arrayOf(stringFlavor, readerFlavor), prefetched.transferDataFlavors)
        assertEquals(listOf(stringFlavor), source.reads)
        assertSame(NoneTransferData, prefetched.getTransferData(readerFlavor))
    }

    @Test
    fun `streams are buffered before the drop is over`() {
        var sourceStream: InputStream? = null
        val source =
            DropSource(
                mapOf(
                    streamFlavor to {
                        ByteArrayInputStream("bytes".encodeToByteArray()).also { sourceStream = it }
                    },
                ),
            )
        val prefetched = PrefetchedTransferable.of(source) { true }
        source.dropOver = true

        val stream = prefetched.getTransferData(streamFlavor) as InputStream
        assertTrue(stream !== sourceStream)
        assertEquals("bytes", stream.readBytes().decodeToString())
    }

    @Test
    fun `streams can be read multiple times without being exhausted`() {
        val source =
            DropSource(
                mapOf(
                    streamFlavor to {
                        ByteArrayInputStream("bytes".encodeToByteArray())
                    },
                ),
            )
        val prefetched = PrefetchedTransferable.of(source) { true }
        source.dropOver = true

        val stream1 = prefetched.getTransferData(streamFlavor) as InputStream
        assertEquals("bytes", stream1.readBytes().decodeToString())

        val stream2 = prefetched.getTransferData(streamFlavor) as InputStream
        assertEquals("bytes", stream2.readBytes().decodeToString())
    }

    @Test
    fun `byte array data is returned as a byte array`() {
        val bytesFlavor = DataFlavor("application/octet-stream;class=\"[B\"")
        val bytes = "bytes".encodeToByteArray()
        val prefetched = PrefetchedTransferable.of(DropSource(mapOf(bytesFlavor to { bytes }))) { true }

        assertSame(bytes, prefetched.getTransferData(bytesFlavor))
    }

    @Test
    fun `null transfer data returns NoneTransferData without throwing`() {
        val source = DropSource(mapOf(stringFlavor to { null }))
        val prefetched = PrefetchedTransferable.of(source) { true }

        assertSame(NoneTransferData, prefetched.getTransferData(stringFlavor))
    }

    @Test
    fun `null transfer data flavors in source is handled safely`() {
        val source =
            object : Transferable {
                override fun getTransferDataFlavors(): Array<DataFlavor>? = null

                override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = false

                override fun getTransferData(flavor: DataFlavor?): Any = throw UnsupportedFlavorException(flavor)
            }
        val prefetched = PrefetchedTransferable.of(source) { true }

        assertEquals(0, prefetched.transferDataFlavors.size)
    }

    @Test
    fun `a failed read rethrows its error`() {
        val source = DropSource(mapOf(stringFlavor to { throw IOException("boom") }))
        val prefetched = PrefetchedTransferable.of(source) { true }

        assertFailsWith<IOException> { prefetched.getTransferData(stringFlavor) }
    }

    @Test
    fun `an unknown flavor is unsupported`() {
        val prefetched = PrefetchedTransferable.of(DropSource(mapOf())) { true }

        assertFailsWith<UnsupportedFlavorException> { prefetched.getTransferData(stringFlavor) }
    }
}
