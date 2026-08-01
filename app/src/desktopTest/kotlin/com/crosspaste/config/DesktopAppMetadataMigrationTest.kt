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
    fun `does not touch an existing valid metadata file`() {
        val dir = tempDir()
        val metadataPersist = OneFilePersist(dir.resolve(".metadata"))
        val legacyPersist = OneFilePersist(dir.resolve("appConfig.json"))
        metadataPersist.save(AppMetadata("current-id"))
        legacyPersist.saveBytes("""{"appInstanceId":"legacy-id"}""".encodeToByteArray())

        migrateAppInstanceIdIfNeeded(metadataPersist, legacyPersist)

        assertEquals(AppMetadata("current-id"), metadataPersist.read(AppMetadata::class))
    }

    @Test
    fun `restores legacy identity when metadata is corrupt and legacy id exists`() {
        val dir = tempDir()
        val metadataPersist = OneFilePersist(dir.resolve(".metadata"))
        val legacyPersist = OneFilePersist(dir.resolve("appConfig.json"))
        metadataPersist.saveBytes("{ not valid json".encodeToByteArray())
        legacyPersist.saveBytes("""{"appInstanceId":"legacy-id"}""".encodeToByteArray())

        migrateAppInstanceIdIfNeeded(metadataPersist, legacyPersist)

        // The original identity survives instead of being rotated (R2-02-006)...
        assertEquals(AppMetadata("legacy-id"), metadataPersist.read(AppMetadata::class))
        // ...and the corrupt payload was quarantined, not destroyed.
        assertEquals(
            "{ not valid json",
            Files.readString(dir.resolve(".metadata.corrupt").toNioPath()),
        )
    }

    @Test
    fun `leaves corrupt metadata untouched when there is no legacy id`() {
        val dir = tempDir()
        val metadataPersist = OneFilePersist(dir.resolve(".metadata"))
        val legacyPersist = OneFilePersist(dir.resolve("appConfig.json"))
        // No legacy identity to restore: migration must leave the corrupt file for
        // AppMetadataRepository to quarantine and regenerate.
        metadataPersist.saveBytes("{ not valid json".encodeToByteArray())

        migrateAppInstanceIdIfNeeded(metadataPersist, legacyPersist)

        assertEquals(
            "{ not valid json",
            Files.readString(dir.resolve(".metadata").toNioPath()),
        )
    }
}
