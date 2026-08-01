package com.crosspaste.utils

import com.crosspaste.presist.FileChunk
import com.crosspaste.presist.FilesChunk
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import okio.EOFException
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFailsWith

class FileChunkIoTest {

    private val fileUtils = getFileUtils()

    @Test
    fun `writeFilesChunk rejects a truncated input channel`() {
        val path = createTempPath()
        try {
            fileUtils.createEmptyPasteFile(path, CHUNK_SIZE).getOrThrow()
            val filesChunk = FilesChunk(listOf(FileChunk(0, CHUNK_SIZE, path)))

            assertFailsWith<EOFException> {
                runBlocking {
                    fileUtils.writeFilesChunk(filesChunk, ByteReadChannel(ByteArray(4)))
                }
            }
        } finally {
            fileUtils.fileSystem.delete(path, mustExist = false)
        }
    }

    @Test
    fun `readFilesChunk rejects a file shorter than the chunk`() {
        val path = createTempPath()
        val output = ByteChannel(true)
        try {
            fileUtils.fileSystem.write(path) { write(ByteArray(4)) }
            val filesChunk = FilesChunk(listOf(FileChunk(0, CHUNK_SIZE, path)))

            assertFailsWith<EOFException> {
                runBlocking {
                    fileUtils.readFilesChunk(filesChunk, output)
                }
            }
        } finally {
            output.cancel(null)
            fileUtils.fileSystem.delete(path, mustExist = false)
        }
    }

    private fun createTempPath() = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "crosspaste-chunk-${Random.nextInt()}"

    private companion object {
        const val CHUNK_SIZE = 10L
    }
}
