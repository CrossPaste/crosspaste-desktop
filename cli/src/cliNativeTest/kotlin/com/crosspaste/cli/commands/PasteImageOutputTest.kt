package com.crosspaste.cli.commands

import com.crosspaste.cli.platform.StdoutWriteException
import com.crosspaste.cli.platform.TerminalImageProtocol
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PasteImageOutputTest {

    private val fs = FileSystem.SYSTEM

    private fun tempDir(): Path {
        val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "paste-image-test-${Random.nextLong().toULong()}"
        fs.createDirectories(dir)
        return dir
    }

    private val pngMagic = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    private fun writePng(
        dir: Path,
        name: String,
        payloadSize: Int,
    ): Path {
        val path = dir / name
        val bytes = pngMagic + ByteArray(payloadSize) { (it % 251).toByte() }
        fs.write(path) { write(bytes) }
        return path
    }

    // region resolveRawImageAction

    @Test
    fun rawActionContract() {
        assertIs<RawImageAction.MissingPaths>(resolveRawImageAction(emptyList()))
        val tooMany = assertIs<RawImageAction.TooManyImages>(resolveRawImageAction(listOf("/a.png", "/b.png")))
        assertEquals(listOf("/a.png", "/b.png"), tooMany.paths)
        assertEquals("/a.png", assertIs<RawImageAction.StreamSingle>(resolveRawImageAction(listOf("/a.png"))).path)
    }

    // endregion

    // region ImageByteStreamer

    @Test
    fun streamerReproducesFileBytesExactly() {
        val dir = tempDir()
        // Larger than the 64 KiB read buffer to force multiple chunks
        val path = writePng(dir, "big.png", 150_000)
        val original = fs.read(path) { readByteArray() }

        val collected = mutableListOf<Byte>()
        var prepared = false
        var flushed = false
        ImageByteStreamer(
            prepareBinary = { prepared = true },
            writeChunk = { bytes, length -> repeat(length) { collected.add(bytes[it]) } },
            flush = { flushed = true },
        ).stream(path.toString())

        assertTrue(prepared, "binary mode must be prepared before writing")
        assertTrue(flushed, "stdout must be flushed after the last chunk")
        assertContentEquals(original, collected.toByteArray())
    }

    @Test
    fun streamerPropagatesMissingFile() {
        assertFailsWith<okio.IOException> {
            ImageByteStreamer(
                prepareBinary = {},
                writeChunk = { _, _ -> },
                flush = {},
            ).stream((tempDir() / "missing.png").toString())
        }
    }

    @Test
    fun streamerPropagatesFlushFailure() {
        val path = writePng(tempDir(), "a.png", 16)
        assertFailsWith<StdoutWriteException> {
            ImageByteStreamer(
                prepareBinary = {},
                writeChunk = { _, _ -> },
                flush = { throw StdoutWriteException("flush failed") },
            ).stream(path.toString())
        }
    }

    // endregion

    // region InlineImageRenderer

    private fun renderer(
        paths: List<String>,
        protocol: TerminalImageProtocol? = TerminalImageProtocol.ITERM,
        maxImages: Int = 4,
        maxTotalBytes: Long = 1_000_000,
    ): Pair<StringBuilder, MutableList<String>> {
        val emitted = StringBuilder()
        val notes = mutableListOf<String>()
        InlineImageRenderer(
            protocol = protocol,
            emit = { emitted.append(it) },
            note = { notes.add(it) },
            maxImages = maxImages,
            maxTotalBytes = maxTotalBytes,
        ).render(paths)
        return emitted to notes
    }

    private fun countItermHeaders(emitted: CharSequence): Int = "File=inline=1".toRegex().findAll(emitted).count()

    @Test
    fun previewStopsAtTheImageCountCeiling() {
        val dir = tempDir()
        val paths = (1..6).map { writePng(dir, "img$it.png", 64).toString() }
        val (emitted, notes) = renderer(paths, maxImages = 4)
        assertEquals(4, countItermHeaders(emitted))
        assertEquals(listOf("(2 image(s) not previewed; file paths listed above)"), notes)
    }

    @Test
    fun previewStopsAtTheTotalByteBudget() {
        val dir = tempDir()
        val paths = (1..3).map { writePng(dir, "img$it.png", 400).toString() }
        // Each file is ~408 bytes; a 900-byte budget fits exactly two
        val (emitted, notes) = renderer(paths, maxTotalBytes = 900)
        assertEquals(2, countItermHeaders(emitted))
        assertEquals(listOf("(1 image(s) not previewed; file paths listed above)"), notes)
    }

    @Test
    fun unreadableImagesAreSkippedWithANote() {
        val dir = tempDir()
        val good = writePng(dir, "good.png", 64).toString()
        val missing = (dir / "missing.png").toString()
        val (emitted, notes) = renderer(listOf(missing, good))
        assertEquals(1, countItermHeaders(emitted))
        assertEquals(1, notes.size)
    }

    @Test
    fun kittyOnlyPreviewsPngPayloads() {
        val dir = tempDir()
        val png = writePng(dir, "a.png", 64).toString()
        val jpegPath = dir / "b.jpg"
        fs.write(jpegPath) { write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())) }
        val (emitted, notes) = renderer(listOf(png, jpegPath.toString()), protocol = TerminalImageProtocol.KITTY)
        assertTrue(emitted.contains("a=T,f=100,"))
        assertEquals(1, "a=T".toRegex().findAll(emitted).count())
        assertEquals(1, notes.size)
    }

    @Test
    fun noProtocolMeansNoOutputAtAll() {
        val dir = tempDir()
        val path = writePng(dir, "a.png", 64).toString()
        val (emitted, notes) = renderer(listOf(path), protocol = null)
        assertEquals(0, emitted.length)
        assertTrue(notes.isEmpty())
    }

    // endregion
}
