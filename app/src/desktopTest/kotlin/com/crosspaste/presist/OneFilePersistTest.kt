package com.crosspaste.presist

import kotlinx.serialization.Serializable
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
