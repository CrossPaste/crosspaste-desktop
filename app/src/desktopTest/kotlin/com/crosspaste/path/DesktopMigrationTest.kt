package com.crosspaste.path

import com.crosspaste.config.AppConfig
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.DriverFactory
import com.crosspaste.notification.NotificationManager
import io.mockk.every
import io.mockk.mockk
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class DesktopMigrationTest {

    private fun createMigration(storagePath: java.io.File): DesktopMigration {
        val appConfig = mockk<AppConfig>()
        every { appConfig.useDefaultStoragePath } returns true

        val configManager = mockk<CommonConfigManager>()
        every { configManager.getCurrentConfig() } returns appConfig

        val driverFactory = mockk<DriverFactory>()
        val notificationManager = mockk<NotificationManager>()
        val platformProvider = mockk<PlatformUserDataPathProvider>()
        every { platformProvider.getUserDefaultStoragePath() } returns storagePath.toOkioPath()

        val userDataPathProvider = UserDataPathProvider(configManager, platformProvider)

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
}
