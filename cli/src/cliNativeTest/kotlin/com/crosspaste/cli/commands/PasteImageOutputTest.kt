package com.crosspaste.cli.commands

import com.crosspaste.cli.api.CliRawImage
import com.crosspaste.cli.platform.StdoutWriteException
import com.crosspaste.cli.platform.TerminalImageProtocol
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.ForwardingSource
import okio.Path
import okio.Source
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

    private suspend fun renderer(
        paths: List<String>,
        protocol: TerminalImageProtocol? = TerminalImageProtocol.ITERM,
        fileSystem: FileSystem = fs,
        maxImages: Int = 4,
        maxCandidates: Int = 16,
        maxTotalBytes: Long = 1_000_000,
        sixelFetch: suspend (Int) -> CliRawImage? = { null },
    ): Pair<StringBuilder, MutableList<String>> {
        val emitted = StringBuilder()
        val notes = mutableListOf<String>()
        InlineImageRenderer(
            protocol = protocol,
            emit = { emitted.append(it) },
            note = { notes.add(it) },
            fileSystem = fileSystem,
            maxImages = maxImages,
            maxCandidates = maxCandidates,
            maxTotalBytes = maxTotalBytes,
            sixelFetch = sixelFetch,
        ).render(paths)
        return emitted to notes
    }

    private fun countItermHeaders(emitted: CharSequence): Int = "File=inline=1".toRegex().findAll(emitted).count()

    @Test
    fun previewStopsAtTheImageCountCeiling() =
        runTest {
            val dir = tempDir()
            val paths = (1..6).map { writePng(dir, "img$it.png", 64).toString() }
            val (emitted, notes) = renderer(paths, maxImages = 4)
            assertEquals(4, countItermHeaders(emitted))
            assertEquals(listOf("(2 image(s) not previewed; file paths listed above)"), notes)
        }

    @Test
    fun previewStopsAtTheTotalByteBudget() =
        runTest {
            val dir = tempDir()
            val paths = (1..3).map { writePng(dir, "img$it.png", 400).toString() }
            // Each file is ~408 bytes; a 900-byte budget fits exactly two
            val (emitted, notes) = renderer(paths, maxTotalBytes = 900)
            assertEquals(2, countItermHeaders(emitted))
            assertEquals(listOf("(1 image(s) not previewed; file paths listed above)"), notes)
        }

    @Test
    fun unreadableImagesAreSkippedWithANote() =
        runTest {
            val dir = tempDir()
            val good = writePng(dir, "good.png", 64).toString()
            val missing = (dir / "missing.png").toString()
            val (emitted, notes) = renderer(listOf(missing, good))
            assertEquals(1, countItermHeaders(emitted))
            assertEquals(1, notes.size)
        }

    @Test
    fun kittyOnlyPreviewsPngPayloads() =
        runTest {
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
    fun unsupportedKittyImagesCountTowardTheCandidateCeiling() =
        runTest {
            val dir = tempDir()
            val jpegPaths =
                (1..4).map { index ->
                    val path = dir / "image$index.jpg"
                    fs.write(path) {
                        write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
                        write(ByteArray(64 * 1024))
                    }
                    path.toString()
                }
            val png = writePng(dir, "later.png", 64).toString()

            val (emitted, notes) =
                renderer(
                    paths = jpegPaths + png,
                    protocol = TerminalImageProtocol.KITTY,
                    maxCandidates = 4,
                )

            assertTrue(emitted.isEmpty())
            assertEquals(listOf("(5 image(s) not previewed; file paths listed above)"), notes)
        }

    @Test
    fun kittyRejectsJpegAfterReadingOnlyItsSignatureBuffer() =
        runTest {
            val dir = tempDir()
            val jpeg = dir / "large.jpg"
            val jpegSize = 1024 * 1024
            fs.write(jpeg) {
                write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
                write(ByteArray(jpegSize - 3))
            }

            var bytesRead = 0L
            val countingFileSystem =
                object : ForwardingFileSystem(fs) {
                    override fun source(file: Path): Source =
                        object : ForwardingSource(super.source(file)) {
                            override fun read(
                                sink: Buffer,
                                byteCount: Long,
                            ): Long =
                                super.read(sink, byteCount).also { read ->
                                    if (read > 0) bytesRead += read
                                }
                        }
                }

            val (emitted, notes) =
                renderer(
                    paths = listOf(jpeg.toString()),
                    protocol = TerminalImageProtocol.KITTY,
                    fileSystem = countingFileSystem,
                )

            assertTrue(emitted.isEmpty())
            assertEquals(1, notes.size)
            assertTrue(bytesRead < jpegSize, "unsupported JPEG payload must not be read in full")
        }

    @Test
    fun noProtocolMeansNoOutputAtAll() =
        runTest {
            val dir = tempDir()
            val path = writePng(dir, "a.png", 64).toString()
            val (emitted, notes) = renderer(listOf(path), protocol = null)
            assertEquals(0, emitted.length)
            assertTrue(notes.isEmpty())
        }

    private val sixelHeader = "${27.toChar()}P0;1;0q"

    /** Opaque single-color image; size in bytes is width * height * 4. */
    private fun rawImage(
        width: Int,
        height: Int,
    ): CliRawImage {
        val rgba = ByteArray(width * height * 4)
        for (i in 0 until width * height) {
            rgba[i * 4] = 0xFF.toByte()
            rgba[i * 4 + 3] = 0xFF.toByte()
        }
        return CliRawImage(width, height, rgba)
    }

    @Test
    fun sixelRendersFetchedPixelsPerIndex() =
        runTest {
            val fetched = mutableListOf<Int>()
            val (emitted, notes) =
                renderer(
                    paths = listOf("/a.png", "/b.jpg"),
                    protocol = TerminalImageProtocol.SIXEL,
                    sixelFetch = { index ->
                        fetched.add(index)
                        rawImage(2, 2)
                    },
                )
            // The fetch index addresses the paste's file list, matching paths
            assertEquals(listOf(0, 1), fetched)
            assertEquals(2, emitted.split(sixelHeader).size - 1)
            assertTrue(emitted.endsWith("${27.toChar()}\\\n"))
            assertTrue(notes.isEmpty())
        }

    @Test
    fun sixelFetchFailureSkipsWithANote() =
        runTest {
            val (emitted, notes) =
                renderer(
                    paths = listOf("/a.png"),
                    protocol = TerminalImageProtocol.SIXEL,
                    sixelFetch = { null },
                )
            assertTrue(emitted.isEmpty())
            assertEquals(listOf("(1 image(s) not previewed; file paths listed above)"), notes)
        }

    @Test
    fun sixelBackendUnavailableStopsFetchingAndSurfacesTheServerMessage() =
        runTest {
            val remedy = "Image decoding is unavailable: install libgl1 and libegl1, then restart CrossPaste."
            var fetches = 0
            val (emitted, notes) =
                renderer(
                    paths = listOf("/a.png", "/b.png", "/c.png"),
                    protocol = TerminalImageProtocol.SIXEL,
                    sixelFetch = {
                        fetches++
                        throw PreviewUnavailableException(remedy)
                    },
                )
            // A process-wide backend failure must not be retried per image
            assertEquals(1, fetches)
            assertTrue(emitted.isEmpty())
            assertEquals(
                listOf("(3 image(s) not previewed; file paths listed above)", remedy),
                notes,
            )
        }

    @Test
    fun sixelBudgetCountsFetchedRgbaBytes() =
        runTest {
            // Each 2x2 fetch is 16 RGBA bytes; a 24-byte budget fits one
            val (emitted, notes) =
                renderer(
                    paths = listOf("/a.png", "/b.png", "/c.png"),
                    protocol = TerminalImageProtocol.SIXEL,
                    maxTotalBytes = 24,
                    sixelFetch = { rawImage(2, 2) },
                )
            assertEquals(1, emitted.split(sixelHeader).size - 1)
            assertEquals(listOf("(2 image(s) not previewed; file paths listed above)"), notes)
        }

    // endregion
}
