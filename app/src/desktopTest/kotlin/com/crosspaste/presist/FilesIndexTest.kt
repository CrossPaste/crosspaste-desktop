package com.crosspaste.presist

import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FilesIndexTest {

    // --- FilesIndexBuilder single file ---

    @Test
    fun `single file smaller than chunk fits in one chunk`() {
        val builder = FilesIndexBuilder(chunkSize = 1024)
        builder.addFile("/file.txt".toPath(), size = 500)
        val index = builder.build()

        assertEquals(1, index.getChunkCount())
        val chunk = index.getChunk(0)
        assertNotNull(chunk)
        assertEquals(1, chunk.fileChunks.size)
        assertEquals(500L, chunk.fileChunks[0].size)
        assertEquals(0L, chunk.fileChunks[0].offset)
    }

    @Test
    fun `single file equal to chunk size fits in one chunk`() {
        val builder = FilesIndexBuilder(chunkSize = 1024)
        builder.addFile("/file.txt".toPath(), size = 1024)
        val index = builder.build()

        assertEquals(1, index.getChunkCount())
    }

    @Test
    fun `single file larger than chunk splits across chunks`() {
        val builder = FilesIndexBuilder(chunkSize = 100)
        builder.addFile("/big.txt".toPath(), size = 250)
        val index = builder.build()

        assertEquals(3, index.getChunkCount())

        val chunk0 = index.getChunk(0)!!
        assertEquals(1, chunk0.fileChunks.size)
        assertEquals(0L, chunk0.fileChunks[0].offset)
        assertEquals(100L, chunk0.fileChunks[0].size)

        val chunk1 = index.getChunk(1)!!
        assertEquals(1, chunk1.fileChunks.size)
        assertEquals(100L, chunk1.fileChunks[0].offset)
        assertEquals(100L, chunk1.fileChunks[0].size)

        val chunk2 = index.getChunk(2)!!
        assertEquals(1, chunk2.fileChunks.size)
        assertEquals(200L, chunk2.fileChunks[0].offset)
        assertEquals(50L, chunk2.fileChunks[0].size)
    }

    // --- Multiple files ---

    @Test
    fun `multiple small files packed into one chunk`() {
        val builder = FilesIndexBuilder(chunkSize = 1024)
        builder.addFile("/a.txt".toPath(), size = 100)
        builder.addFile("/b.txt".toPath(), size = 200)
        builder.addFile("/c.txt".toPath(), size = 300)
        val index = builder.build()

        assertEquals(1, index.getChunkCount())
        val chunk = index.getChunk(0)!!
        assertEquals(3, chunk.fileChunks.size)
    }

    @Test
    fun `multiple files split across chunks at boundary`() {
        val builder = FilesIndexBuilder(chunkSize = 200)
        builder.addFile("/a.txt".toPath(), size = 150)
        builder.addFile("/b.txt".toPath(), size = 150)
        val index = builder.build()

        assertEquals(2, index.getChunkCount())

        // First chunk: a.txt (150) + first 50 of b.txt
        val chunk0 = index.getChunk(0)!!
        assertEquals(2, chunk0.fileChunks.size)
        assertEquals(150L, chunk0.fileChunks[0].size) // a.txt
        assertEquals(50L, chunk0.fileChunks[1].size) // first part of b.txt

        // Second chunk: remaining 100 of b.txt
        val chunk1 = index.getChunk(1)!!
        assertEquals(1, chunk1.fileChunks.size)
        assertEquals(100L, chunk1.fileChunks[0].size)
        assertEquals(50L, chunk1.fileChunks[0].offset)
    }

    // --- Edge cases ---

    @Test
    fun `empty builder produces zero chunks`() {
        val builder = FilesIndexBuilder(chunkSize = 1024)
        val index = builder.build()
        assertEquals(0, index.getChunkCount())
    }

    @Test
    fun `getChunk returns null for out-of-range index`() {
        val builder = FilesIndexBuilder(chunkSize = 1024)
        builder.addFile("/file.txt".toPath(), size = 100)
        val index = builder.build()

        assertNull(index.getChunk(1))
        assertNull(index.getChunk(-1))
    }

    @Test
    fun `getChunk returns null for negative index`() {
        val builder = FilesIndexBuilder(chunkSize = 1024)
        val index = builder.build()
        assertNull(index.getChunk(-1))
    }

    // --- FileChunk data ---

    @Test
    fun `FileChunk preserves offset size and path`() {
        val path = "/test/file.txt".toPath()
        val chunk = FileChunk(offset = 100L, size = 50L, path = path)
        assertEquals(100L, chunk.offset)
        assertEquals(50L, chunk.size)
        assertEquals(path, chunk.path)
    }

    @Test
    fun `FileChunk toString contains filename`() {
        val chunk = FileChunk(offset = 0, size = 100, path = "/test/my_file.txt".toPath())
        val str = chunk.toString()
        assert(str.contains("my_file.txt"))
    }

    @Test
    fun `FilesChunk toString contains all file chunks`() {
        val chunks =
            FilesChunk(
                listOf(
                    FileChunk(0, 100, "/a.txt".toPath()),
                    FileChunk(0, 200, "/b.txt".toPath()),
                ),
            )
        val str = chunks.toString()
        assert(str.contains("a.txt"))
        assert(str.contains("b.txt"))
    }

    // --- Large file scenario ---

    @Test
    fun `very large file produces correct number of chunks`() {
        val chunkSize = 1024L * 1024 // 1MB chunks
        val fileSize = 5L * 1024 * 1024 + 512 * 1024 // 5.5MB
        val builder = FilesIndexBuilder(chunkSize = chunkSize)
        builder.addFile("/big.bin".toPath(), size = fileSize)
        val index = builder.build()

        assertEquals(6, index.getChunkCount()) // 5 full + 1 partial

        // Verify total size adds up
        var totalSize = 0L
        for (i in 0 until index.getChunkCount()) {
            val chunk = index.getChunk(i)!!
            for (fc in chunk.fileChunks) {
                totalSize += fc.size
            }
        }
        assertEquals(fileSize, totalSize)
    }
}
