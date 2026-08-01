package com.crosspaste.presist

import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FilesIndexValidationTest {

    @Test
    fun `builder rejects invalid chunk sizes`() {
        assertFailsWith<IllegalArgumentException> { FilesIndexBuilder(0) }
        assertFailsWith<IllegalArgumentException> { FilesIndexBuilder(-1) }
        assertFailsWith<IllegalArgumentException> {
            FilesIndexBuilder(FileTransferResourceLimits.MAX_CHUNK_SIZE + 1)
        }
    }

    @Test
    fun `builder rejects negative file size`() {
        val builder = FilesIndexBuilder(1024)

        assertFailsWith<IllegalArgumentException> {
            builder.addFile("negative.bin".toPath(), -1)
        }
    }

    @Test
    fun `builder rejects cumulative size above transfer limit`() {
        val builder = FilesIndexBuilder(FileTransferResourceLimits.MAX_CHUNK_SIZE)
        builder.addFile("max.bin".toPath(), FileTransferResourceLimits.MAX_TOTAL_SIZE)

        assertFailsWith<IllegalArgumentException> {
            builder.addFile("overflow.bin".toPath(), 1)
        }
    }

    @Test
    fun `builder rejects excessive chunk count before constructing chunks`() {
        val builder = FilesIndexBuilder(1)

        assertFailsWith<IllegalArgumentException> {
            builder.addFile(
                "too-many-chunks.bin".toPath(),
                FileTransferResourceLimits.MAX_CHUNK_COUNT.toLong() + 1,
            )
        }
    }

    @Test
    fun `file after exact chunk boundary has no zero-size fragment`() {
        val builder = FilesIndexBuilder(10)
        builder.addFile("first.bin".toPath(), 10)
        builder.addFile("second.bin".toPath(), 5)

        val index = builder.build()

        assertEquals(2, index.getChunkCount())
        assertEquals(listOf(10L), index.getChunk(0)!!.fileChunks.map { it.size })
        assertEquals(listOf(5L), index.getChunk(1)!!.fileChunks.map { it.size })
    }

    @Test
    fun `chunk builder rejects inconsistent remaining size`() {
        val builder = FilesChunkBuilder(10)

        assertFailsWith<IllegalArgumentException> {
            builder.addFile("file.bin".toPath(), remainingSize = 11, size = 10)
        }
    }

    @Test
    fun `file chunk rejects invalid ranges and aggregate size`() {
        val path = "file.bin".toPath()
        assertFailsWith<IllegalArgumentException> { FileChunk(offset = -1, size = 1, path = path) }
        assertFailsWith<IllegalArgumentException> {
            FileChunk(offset = Long.MAX_VALUE, size = 1, path = path)
        }
        assertFailsWith<IllegalArgumentException> {
            FilesChunk(
                listOf(
                    FileChunk(offset = 0, size = FileTransferResourceLimits.MAX_CHUNK_SIZE, path = path),
                    FileChunk(offset = 0, size = 1, path = path),
                ),
            )
        }
    }
}
