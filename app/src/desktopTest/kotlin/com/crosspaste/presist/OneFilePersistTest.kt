package com.crosspaste.presist

import kotlinx.serialization.Serializable
import okio.IOException
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OneFilePersistTest {

    @Serializable
    private data class Payload(
        val value: String,
    )

    private fun tempDir() = Files.createTempDirectory("oneFilePersistTest").toOkioPath()

    @Test
    fun `save writes content readable by read`() {
        val persist = OneFilePersist(tempDir().resolve("payload.json"))

        persist.save(Payload("hello"))

        assertEquals(Payload("hello"), persist.read(Payload::class))
    }

    @Test
    fun `save leaves no temp file behind`() {
        val dir = tempDir()
        val persist = OneFilePersist(dir.resolve("payload.json"))

        persist.save(Payload("hello"))
        persist.save(Payload("overwritten"))

        val entries = Files.list(dir.toNioPath()).use { it.map { p -> p.fileName.toString() }.toList() }
        assertEquals(listOf("payload.json"), entries)
        assertEquals(Payload("overwritten"), persist.read(Payload::class))
    }

    @Test
    fun `save creates missing parent directories`() {
        val persist = OneFilePersist(tempDir().resolve("nested/deeper/payload.json"))

        persist.save(Payload("hello"))

        assertEquals(Payload("hello"), persist.read(Payload::class))
    }

    @Test
    fun `exists reflects file presence`() {
        val persist = OneFilePersist(tempDir().resolve("payload.json"))

        assertFalse(persist.exists())
        persist.save(Payload("hello"))
        assertTrue(persist.exists())
        persist.delete()
        assertFalse(persist.exists())
    }

    @Test
    fun `quarantine moves the file aside preserving its bytes`() {
        val dir = tempDir()
        val persist = OneFilePersist(dir.resolve("payload.json"))
        persist.saveBytes("not json".encodeToByteArray())

        val backup = persist.quarantine()

        assertEquals(dir.resolve("payload.json.corrupt"), backup)
        assertFalse(persist.exists())
        assertEquals("not json", Files.readString(backup!!.toNioPath()))
    }

    @Test
    fun `quarantine returns null when there is no file`() {
        val persist = OneFilePersist(tempDir().resolve("payload.json"))

        assertNull(persist.quarantine())
    }

    @Test
    fun `quarantine replaces an existing corrupt backup`() {
        val dir = tempDir()
        val persist = OneFilePersist(dir.resolve("payload.json"))
        Files.writeString(dir.resolve("payload.json.corrupt").toNioPath(), "old backup")
        persist.saveBytes("new corrupt".encodeToByteArray())

        val backup = persist.quarantine()

        assertEquals("new corrupt", Files.readString(backup!!.toNioPath()))
    }

    @Test
    fun `failed write cleans up its temp file and propagates the error`() {
        val dir = tempDir()
        // A non-empty directory at the target path: the temp write succeeds but the
        // atomic move fails, which must clean the temp file and rethrow.
        val target = dir.resolve("payload.json")
        Files.createDirectory(target.toNioPath())
        Files.writeString(target.resolve("occupant").toNioPath(), "keep")
        val persist = OneFilePersist(target)

        assertFailsWith<IOException> { persist.save(Payload("hello")) }

        val leftovers =
            Files.list(dir.toNioPath()).use { stream ->
                stream.map { it.fileName.toString() }.toList()
            }
        assertEquals(listOf("payload.json"), leftovers)
        assertEquals("keep", Files.readString(target.resolve("occupant").toNioPath()))
    }
}
