package com.crosspaste.config

import com.crosspaste.presist.OneFilePersist
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopAppMetadataMigrationTest {

    private fun tempDir() = Files.createTempDirectory("appMetadataMigrationTest").toOkioPath()

    @Test
    fun `migrates legacy appInstanceId when metadata file is absent`() {
        val dir = tempDir()
        val metadataPersist = OneFilePersist(dir.resolve(".metadata"))
        val legacyPersist = OneFilePersist(dir.resolve("appConfig.json"))
        legacyPersist.saveBytes("""{"appInstanceId":"legacy-id","language":"en"}""".encodeToByteArray())

        migrateAppInstanceIdIfNeeded(metadataPersist, legacyPersist)

        assertEquals(AppMetadata("legacy-id"), metadataPersist.read(AppMetadata::class))
    }

    @Test
    fun `does nothing when neither metadata nor legacy config exists`() {
        val dir = tempDir()
        val metadataPersist = OneFilePersist(dir.resolve(".metadata"))
        val legacyPersist = OneFilePersist(dir.resolve("appConfig.json"))

        migrateAppInstanceIdIfNeeded(metadataPersist, legacyPersist)

        assertFalse(metadataPersist.exists())
    }

    @Test
    fun `never overwrites an existing metadata file even if unreadable`() {
        val dir = tempDir()
        val metadataPersist = OneFilePersist(dir.resolve(".metadata"))
        val legacyPersist = OneFilePersist(dir.resolve("appConfig.json"))
        // Corrupt metadata: migration must leave it for AppMetadataRepository to
        // quarantine, not silently replace it with the legacy identity (R2-02-006).
        metadataPersist.saveBytes("{ not valid json".encodeToByteArray())
        legacyPersist.saveBytes("""{"appInstanceId":"legacy-id"}""".encodeToByteArray())

        migrateAppInstanceIdIfNeeded(metadataPersist, legacyPersist)

        assertEquals(
            "{ not valid json",
            Files.readString(dir.resolve(".metadata").toNioPath()),
        )
    }
}
