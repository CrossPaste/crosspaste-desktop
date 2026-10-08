package com.crosspaste.path

import com.crosspaste.config.CommonConfigManager
import com.crosspaste.config.DesktopAppConfig
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.db.DriverFactory
import com.crosspaste.notification.NotificationManager
import com.crosspaste.utils.DesktopLocaleUtils
import io.mockk.every
import io.mockk.mockk
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopMigrationTest {

    private fun createMigration(storagePath: java.io.File): DesktopMigration {
        val appConfig = mockk<DesktopAppConfig>()
        every { appConfig.useDefaultStoragePath } returns true

        val configManager = mockk<DesktopConfigManager>()
        every { configManager.getCurrentConfig() } returns appConfig

        val driverFactory = mockk<DriverFactory>()
        val notificationManager = mockk<NotificationManager>()
        val platformProvider = mockk<PlatformUserDataPathProvider>()
        every { platformProvider.getUserDefaultStoragePath() } returns storagePath.toOkioPath()

        val userDataPathProvider = UserDataPathProvider(configManager as CommonConfigManager, platformProvider)

        return DesktopMigration(
            configManager = configManager,
            driverFactory = driverFactory,
            notificationManager = notificationManager,
            userDataPathProvider = userDataPathProvider,
        )
    }

    @Test
    fun `checkMigrationPath returns directory_not_exist for non-existent path`() {
        val tempDir = Files.createTempDirectory("migration-test").toFile()
        tempDir.deleteOnExit()
        val migration = createMigration(tempDir)

        val nonExistent = tempDir.resolve("does_not_exist").toOkioPath()
        val result = migration.checkMigrationPath(nonExistent)
        assertEquals("directory_not_exist", result)
    }

    @Test
    fun `checkMigrationPath returns not_a_directory for file path`() {
        val tempDir = Files.createTempDirectory("migration-test").toFile()
        tempDir.deleteOnExit()
        val migration = createMigration(tempDir)

        val file = tempDir.resolve("file.txt")
        file.writeText("data")
        file.deleteOnExit()

        val result = migration.checkMigrationPath(file.toOkioPath())
        assertEquals("not_a_directory", result)
    }

    @Test
    fun `checkMigrationPath rejects migration target that is child of current storage`() {
        // current storage = tempDir, migration target = tempDir/child
        // migrationPath starts with currentStoragePath → "cant_select_parent_directory"
        val tempDir = Files.createTempDirectory("migration-test").toFile()
        tempDir.deleteOnExit()
        val migration = createMigration(tempDir)

        val childDir = tempDir.resolve("child")
        childDir.mkdirs()
        childDir.deleteOnExit()

        val result = migration.checkMigrationPath(childDir.toOkioPath())
        assertEquals("cant_select_parent_directory", result)
    }

    @Test
    fun `checkMigrationPath rejects migration target that is parent of current storage`() {
        // current storage = nested/storage, migration target = tempDir (ancestor)
        // currentStoragePath starts with migrationPath → "cant_select_child_directory"
        val tempDir = Files.createTempDirectory("migration-test").toFile()
        tempDir.deleteOnExit()

        val storageDir = tempDir.resolve("nested").resolve("storage")
        storageDir.mkdirs()
        storageDir.deleteOnExit()

        val migration = createMigration(storageDir)
        val result = migration.checkMigrationPath(tempDir.toOkioPath())
        assertEquals("cant_select_child_directory", result)
    }

    @Test
    fun `checkMigrationPath returns directory_not_empty for non-empty directory`() {
        val tempDir = Files.createTempDirectory("migration-test").toFile()
        tempDir.deleteOnExit()

        val storageDir = tempDir.resolve("storage")
        storageDir.mkdirs()
        storageDir.deleteOnExit()

        val migrationDir = tempDir.resolve("migration")
        migrationDir.mkdirs()
        migrationDir.deleteOnExit()

        val file = migrationDir.resolve("existing.txt")
        file.writeText("content")
        file.deleteOnExit()

        val migration = createMigration(storageDir)
        val result = migration.checkMigrationPath(migrationDir.toOkioPath())
        assertEquals("directory_not_empty", result)
    }

    @Test
    fun `checkMigrationPath ignores hidden files when checking emptiness`() {
        val tempDir = Files.createTempDirectory("migration-test").toFile()
        tempDir.deleteOnExit()

        val storageDir = tempDir.resolve("storage")
        storageDir.mkdirs()
        storageDir.deleteOnExit()

        val migrationDir = tempDir.resolve("migration")
        migrationDir.mkdirs()
        migrationDir.deleteOnExit()

        // Only hidden files present - directory should be considered empty
        val hiddenFile = migrationDir.resolve(".DS_Store")
        hiddenFile.writeText("hidden")
        hiddenFile.deleteOnExit()

        val migration = createMigration(storageDir)
        val result = migration.checkMigrationPath(migrationDir.toOkioPath())
        // Should NOT be "directory_not_empty" since only hidden files exist
        assertNotEquals("directory_not_empty", result)
    }

    @Test
    fun `checkMigrationPath returns null for valid empty writable directory`() {
        val tempDir = Files.createTempDirectory("migration-test").toFile()
        tempDir.deleteOnExit()

        val storageDir = tempDir.resolve("storage")
        storageDir.mkdirs()
        storageDir.deleteOnExit()

        val migrationDir = tempDir.resolve("migration")
        migrationDir.mkdirs()
        migrationDir.deleteOnExit()

        val migration = createMigration(storageDir)
        val result = migration.checkMigrationPath(migrationDir.toOkioPath())
        assertNull(result, "Valid empty writable directory should return null (no error)")
    }

    @Test
    fun `checkMigrationPath validates write permission`() {
        val tempDir = Files.createTempDirectory("migration-test").toFile()
        tempDir.deleteOnExit()

        val storageDir = tempDir.resolve("storage")
        storageDir.mkdirs()
        storageDir.deleteOnExit()

        val readOnlyDir = tempDir.resolve("readonly")
        readOnlyDir.mkdirs()
        readOnlyDir.deleteOnExit()

        readOnlyDir.setWritable(false)
        try {
            val migration = createMigration(storageDir)
            val result = migration.checkMigrationPath(readOnlyDir.toOkioPath())
            assertEquals("no_write_permission", result)
        } finally {
            readOnlyDir.setWritable(true)
        }
    }

    private class Fixture(
        val migration: DesktopMigration,
        val configManager: DesktopConfigManager,
        val configDir: java.io.File,
        val storageDir: java.io.File,
        val migrationDir: java.io.File,
    )

    /** A real config manager over a temp file, so storage paths follow the saved config. */
    private fun createFixture(): Fixture {
        val tempDir = Files.createTempDirectory("migration-test").toFile()
        tempDir.deleteOnExit()
        val configDir = tempDir.resolve("config").apply { mkdirs() }
        val storageDir = tempDir.resolve("storage").apply { mkdirs() }
        val migrationDir = tempDir.resolve("migration").apply { mkdirs() }

        val configManager = DesktopConfigManager(configDir.toOkioPath(), DesktopLocaleUtils)
        val platformProvider = mockk<PlatformUserDataPathProvider>()
        every { platformProvider.getUserDefaultStoragePath() } returns storageDir.toOkioPath()
        val driverFactory = mockk<DriverFactory>()
        every { driverFactory.dbName } returns "crosspaste.db"

        val migration =
            DesktopMigration(
                configManager = configManager,
                driverFactory = driverFactory,
                notificationManager = mockk(relaxed = true),
                userDataPathProvider = UserDataPathProvider(configManager as CommonConfigManager, platformProvider),
            )

        // An existing store: the database plus one paste file.
        storageDir
            .resolve("data")
            .apply { mkdirs() }
            .resolve("crosspaste.db")
            .writeText("db")
        storageDir
            .resolve("files")
            .apply { mkdirs() }
            .resolve("a.txt")
            .writeText("file")
        return Fixture(migration, configManager, configDir, storageDir, migrationDir)
    }

    @Test
    fun `migration moves the store and points the config at it`() {
        val fixture = createFixture()
        fixture.storageDir
            .resolve("data")
            .resolve("crosspaste.db-wal")
            .writeText("wal")
        fixture.storageDir
            .resolve("opengraph")
            .apply { mkdirs() }
            .resolve("og.png")
            .writeText("og")

        fixture.migration.migration(fixture.migrationDir.toOkioPath())

        val config = fixture.configManager.getCurrentConfig()
        assertFalse(config.useDefaultStoragePath)
        assertEquals(fixture.migrationDir.toOkioPath().toString(), config.storagePath)
        assertEquals("db", fixture.migrationDir.resolve("data/crosspaste.db").readText())
        assertEquals("wal", fixture.migrationDir.resolve("data/crosspaste.db-wal").readText())
        assertEquals("file", fixture.migrationDir.resolve("files/a.txt").readText())
        assertEquals("og", fixture.migrationDir.resolve("opengraph/og.png").readText())
        assertFalse(fixture.storageDir.resolve("data").exists())
        assertFalse(fixture.storageDir.resolve("opengraph").exists())
        assertFalse(fixture.storageDir.resolve("files").exists())
    }

    @Test
    fun `migration keeps the origin store when the new path cannot be saved`() {
        val fixture = createFixture()
        fixture.configDir.setWritable(false)
        try {
            assertFailsWith<Exception> {
                fixture.migration.migration(fixture.migrationDir.toOkioPath())
            }
        } finally {
            fixture.configDir.setWritable(true)
        }

        assertTrue(fixture.configManager.getCurrentConfig().useDefaultStoragePath)
        assertEquals("db", fixture.storageDir.resolve("data/crosspaste.db").readText())
        assertEquals("file", fixture.storageDir.resolve("files/a.txt").readText())
        assertTrue(fixture.migrationDir.listFiles().isNullOrEmpty(), "the partial copy is removed")
    }

    @Test
    fun `migration keeps the origin store when copying files fails`() {
        val fixture = createFixture()
        val file = fixture.storageDir.resolve("files/a.txt")
        file.setReadable(false)
        try {
            assertFailsWith<Exception> {
                fixture.migration.migration(fixture.migrationDir.toOkioPath())
            }
        } finally {
            file.setReadable(true)
        }

        assertTrue(fixture.configManager.getCurrentConfig().useDefaultStoragePath)
        assertEquals("db", fixture.storageDir.resolve("data/crosspaste.db").readText())
        assertEquals("file", fixture.storageDir.resolve("files/a.txt").readText())
        assertTrue(fixture.migrationDir.listFiles().isNullOrEmpty(), "the partial copy is removed")
    }
}
