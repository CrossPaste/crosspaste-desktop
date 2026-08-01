package com.crosspaste.config

import com.crosspaste.presist.OneFilePersist
import com.crosspaste.utils.DeviceUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okio.IOException
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppMetadataRepositoryTest {

    private val deviceUtils =
        mockk<DeviceUtils> {
            every { createAppInstanceId() } returns "generated-id"
        }

    private fun tempDir() = Files.createTempDirectory("appMetadataTest").toOkioPath()

    @Test
    fun `missing file creates and persists a new identity`() {
        val persist = OneFilePersist(tempDir().resolve(".metadata"))
        val repository = AppMetadataRepository(persist, deviceUtils)

        assertEquals("generated-id", repository.appInstanceId)
        assertEquals(AppMetadata("generated-id"), persist.read(AppMetadata::class))
    }

    @Test
    fun `existing valid file is reused without rotation`() {
        val persist = OneFilePersist(tempDir().resolve(".metadata"))
        persist.save(AppMetadata("stored-id"))

        val repository = AppMetadataRepository(persist, deviceUtils)

        assertEquals("stored-id", repository.appInstanceId)
        verify(exactly = 0) { deviceUtils.createAppInstanceId() }

        // A second repository over the same file must see the same identity.
        assertEquals("stored-id", AppMetadataRepository(persist, deviceUtils).appInstanceId)
    }

    @Test
    fun `corrupt file is quarantined before a new identity is generated`() {
        val dir = tempDir()
        val persist = OneFilePersist(dir.resolve(".metadata"))
        persist.saveBytes("{ not valid json".encodeToByteArray())

        val repository = AppMetadataRepository(persist, deviceUtils)

        assertEquals("generated-id", repository.appInstanceId)
        // The corrupt payload is preserved for inspection...
        val backup = dir.resolve(".metadata.corrupt")
        assertTrue(Files.exists(backup.toNioPath()))
        assertEquals("{ not valid json", Files.readString(backup.toNioPath()))
        // ...and the new identity is persisted in its place.
        assertEquals(AppMetadata("generated-id"), persist.read(AppMetadata::class))
    }

    @Test
    fun `quarantine failure propagates instead of rotating the identity`() {
        val dir = tempDir()
        val persist = OneFilePersist(dir.resolve(".metadata"))
        persist.saveBytes("{ not valid json".encodeToByteArray())
        // A non-empty directory at the backup path makes quarantine's atomic move fail.
        val backupPath = dir.resolve(".metadata.corrupt")
        Files.createDirectory(backupPath.toNioPath())
        Files.writeString(backupPath.resolve("occupant").toNioPath(), "keep")
        val repository = AppMetadataRepository(persist, deviceUtils)

        assertFailsWith<IOException> { repository.appInstanceId }

        // No identity was generated and the corrupt file is still in place.
        verify(exactly = 0) { deviceUtils.createAppInstanceId() }
        assertEquals("{ not valid json", Files.readString(dir.resolve(".metadata").toNioPath()))
    }

    @Test
    fun `read IO error fails instead of rotating the identity`() {
        val dir = tempDir()
        // A directory at the metadata path makes the read fail with an I/O error
        // while fileSystem.exists() still returns true — modelling an unreadable file.
        val metadataPath = dir.resolve(".metadata")
        Files.createDirectory(metadataPath.toNioPath())
        val repository = AppMetadataRepository(OneFilePersist(metadataPath), deviceUtils)

        assertFailsWith<IOException> { repository.appInstanceId }

        // No identity was generated, nothing was quarantined or overwritten.
        verify(exactly = 0) { deviceUtils.createAppInstanceId() }
        assertFalse(Files.exists(dir.resolve(".metadata.corrupt").toNioPath()))
        assertTrue(Files.isDirectory(metadataPath.toNioPath()))
    }
}
