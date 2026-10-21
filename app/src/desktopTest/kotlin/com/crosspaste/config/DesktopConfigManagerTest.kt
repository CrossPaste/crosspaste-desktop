package com.crosspaste.config

import com.crosspaste.utils.DesktopLocaleUtils
import okio.Path
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopConfigManagerTest {

    private fun tempConfigDir(): Path {
        val configDir = Files.createTempDirectory("configDir").toOkioPath()
        configDir.toFile().deleteOnExit()
        return configDir
    }

    private fun createConfigManager(configDir: Path = tempConfigDir()): DesktopConfigManager =
        DesktopConfigManager(configDir, DesktopLocaleUtils)

    private fun settingsFile(configDir: Path) = configDir.resolve(ConfigScope.SETTINGS.fileName).toFile()

    private fun stateFile(configDir: Path) = configDir.resolve(ConfigScope.RUNTIME_STATE.fileName).toFile()

    private fun legacyFile(configDir: Path) = configDir.resolve(LegacyJsonConfigImporter.FILE_NAME).toFile()

    @Test
    fun `initial config has default values`() {
        val manager = createConfigManager()
        val config = manager.getCurrentConfig()
        assertEquals(13129, config.port)
        assertTrue(config.enableAutoStartUp)
        assertTrue(config.enablePasteboardListening)
        assertTrue(config.enableExpirationCleanup)
        assertTrue(config.enableThresholdCleanup)
        assertEquals(2048, config.maxStorage)
        assertEquals(20, config.cleanupPercentage)
    }

    @Test
    fun `updateConfig updates single boolean field`() {
        val manager = createConfigManager()
        manager.updateConfig("enableAutoStartUp", false)
        assertEquals(false, manager.getCurrentConfig().enableAutoStartUp)
    }

    @Test
    fun `updateConfig updates single int field`() {
        val manager = createConfigManager()
        manager.updateConfig("port", 9999)
        assertEquals(9999, manager.getCurrentConfig().port)
    }

    @Test
    fun `updateConfig updates single long field`() {
        val manager = createConfigManager()
        manager.updateConfig("maxStorage", 4096L)
        assertEquals(4096L, manager.getCurrentConfig().maxStorage)
    }

    @Test
    fun `updateConfig updates single string field`() {
        val manager = createConfigManager()
        manager.updateConfig("language", "zh")
        assertEquals("zh", manager.getCurrentConfig().language)
    }

    @Test
    fun `updateConfig coerces values to the key type`() {
        val manager = createConfigManager()
        manager.updateConfig("port", "8080")
        manager.updateConfig("enableAutoStartUp", "false")
        assertEquals(8080, manager.getCurrentConfig().port)
        assertFalse(manager.getCurrentConfig().enableAutoStartUp)
    }

    @Test
    fun `updateConfig batch updates multiple fields`() {
        val manager = createConfigManager()
        manager.updateConfig(
            keys = listOf("port", "enableAutoStartUp", "language"),
            values = listOf(8080, false, "ja"),
        )
        val config = manager.getCurrentConfig()
        assertEquals(8080, config.port)
        assertEquals(false, config.enableAutoStartUp)
        assertEquals("ja", config.language)
    }

    @Test
    fun `config state flow emits updated value`() {
        val manager = createConfigManager()
        val initialPort = manager.config.value.port
        manager.updateConfig("port", 7777)
        assertEquals(7777, manager.config.value.port)
        assertNotEquals(initialPort, manager.config.value.port)
    }

    @Test
    fun `config persists across manager instances`() {
        val configDir = tempConfigDir()

        val manager1 = createConfigManager(configDir)
        manager1.updateConfig("port", 5555)
        manager1.updateConfig("lastPasteboardChangeCount", 3)
        manager1.close()

        val manager2 = createConfigManager(configDir)
        assertEquals(5555, manager2.getCurrentConfig().port)
        assertEquals(3, manager2.getCurrentConfig().lastPasteboardChangeCount)
    }

    @Test
    fun `settings and runtime state are stored in separate files`() {
        val configDir = tempConfigDir()
        val manager = createConfigManager(configDir)

        manager.updateConfig("lastPasteboardChangeCount", 7)
        assertTrue(stateFile(configDir).exists())
        assertFalse(settingsFile(configDir).exists(), "a runtime-state write must not touch the settings file")

        manager.updateConfig("port", 5555)
        assertTrue(settingsFile(configDir).exists())
        assertEquals(7, manager.getCurrentConfig().lastPasteboardChangeCount)
        assertEquals(5555, manager.getCurrentConfig().port)
    }

    @Test
    fun `updateConfig only changes the keys it is given`() {
        val configDir = tempConfigDir()
        val manager = createConfigManager(configDir)
        manager.updateConfig("port", 5555)
        manager.updateConfig("language", "zh")
        manager.close()

        val reopened = createConfigManager(configDir)
        assertEquals(5555, reopened.getCurrentConfig().port)
        assertEquals("zh", reopened.getCurrentConfig().language)
    }

    @Test
    fun `loadConfig reads defaults for a missing store`() {
        val manager = createConfigManager()
        val loaded = manager.loadConfig()
        assertNotNull(loaded)
        assertEquals(13129, loaded.port)
    }

    @Test
    fun `batch updateConfig requires equal sized lists`() {
        val manager = createConfigManager()
        assertFailsWith<IllegalArgumentException> {
            manager.updateConfig(
                keys = listOf("port"),
                values = listOf(1, 2),
            )
        }
    }

    @Test
    fun `updateConfig rejects an unknown key without writing anything`() {
        val configDir = tempConfigDir()
        val manager = createConfigManager(configDir)
        assertFailsWith<IllegalArgumentException> {
            manager.updateConfig(listOf("port", "noSuchKey"), listOf(1234, true))
        }
        assertEquals(13129, manager.getCurrentConfig().port)
        assertFalse(settingsFile(configDir).exists())
    }

    @Test
    fun `updateConfig updates sync content type controls`() {
        val manager = createConfigManager()
        manager.updateConfig("enableSyncText", false)
        assertEquals(false, manager.getCurrentConfig().enableSyncText)
        manager.updateConfig("enableSyncImage", false)
        assertEquals(false, manager.getCurrentConfig().enableSyncImage)
    }

    @Test
    fun `corrupt store is quarantined before defaults are written`() {
        val configDir = tempConfigDir()
        // A length-delimited field announcing more bytes than follow: never a valid preferences proto.
        val corrupt = byteArrayOf(0x0A, 0x7F)
        settingsFile(configDir).writeBytes(corrupt)

        val manager = createConfigManager(configDir)
        assertEquals(13129, manager.getCurrentConfig().port)

        manager.updateConfig("port", 5555)

        val backup = configDir.resolve("${ConfigScope.SETTINGS.fileName}.corrupt").toFile()
        assertTrue(backup.exists(), "the corrupt payload must be kept for inspection")
        assertTrue(corrupt.contentEquals(backup.readBytes()))
        assertEquals(5555, manager.loadConfig()?.port)
    }

    @Test
    fun `unreadable store is never overwritten with defaults`() {
        val configDir = tempConfigDir()
        val configFile = settingsFile(configDir)
        val original = createConfigManager(configDir).also { it.updateConfig("port", 5555) }
        original.close()
        val storedBytes = configFile.readBytes()
        assertTrue(configFile.setReadable(false))
        try {
            val manager = createConfigManager(configDir)
            // Unreadable at startup: this session runs on defaults...
            assertEquals(13129, manager.getCurrentConfig().port)

            manager.updateConfig("enableAutoStartUp", false)

            // ...and a write that cannot read the file first is refused rather than
            // replacing the user's settings with these defaults.
            assertTrue(manager.getCurrentConfig().enableAutoStartUp)
        } finally {
            configFile.setReadable(true)
        }
        assertTrue(storedBytes.contentEquals(configFile.readBytes()))
        assertFalse(configDir.resolve("${ConfigScope.SETTINGS.fileName}.corrupt").toFile().exists())
    }

    @Test
    fun `legacy appConfig json is imported into both stores and archived`() {
        val configDir = tempConfigDir()
        val legacy =
            """
            {"appInstanceId":"legacy-id","language":"zh","port":5555,"maxStorage":4096,
             "enableAutoStartUp":false,"lastPasteboardChangeCount":9,"notAKey":1}
            """.trimIndent()
        legacyFile(configDir).writeText(legacy)

        val manager = createConfigManager(configDir)
        val config = manager.getCurrentConfig()
        assertEquals("zh", config.language)
        assertEquals(5555, config.port)
        assertEquals(4096L, config.maxStorage)
        assertFalse(config.enableAutoStartUp)
        assertEquals(9, config.lastPasteboardChangeCount)
        // Keys the document does not mention keep their defaults.
        assertTrue(config.enablePasteboardListening)

        assertFalse(legacyFile(configDir).exists(), "the legacy document must not remain a second source of truth")
        val archive =
            configDir.resolve(
                "${LegacyJsonConfigImporter.FILE_NAME}${LegacyJsonConfigImporter.MIGRATED_SUFFIX}",
            )
        assertEquals(legacy, archive.toFile().readText())
        assertTrue(settingsFile(configDir).exists())
        assertTrue(stateFile(configDir).exists())

        manager.close()
        val reopened = createConfigManager(configDir)
        assertEquals(5555, reopened.getCurrentConfig().port)
        assertEquals(9, reopened.getCurrentConfig().lastPasteboardChangeCount)
    }

    @Test
    fun `legacy value of the wrong type is skipped`() {
        val configDir = tempConfigDir()
        legacyFile(configDir).writeText("""{"port":"not a number","language":"zh"}""")

        val manager = createConfigManager(configDir)

        assertEquals(13129, manager.getCurrentConfig().port)
        assertEquals("zh", manager.getCurrentConfig().language)
        assertFalse(legacyFile(configDir).exists())
    }

    @Test
    fun `corrupt legacy appConfig json is quarantined and ignored`() {
        val configDir = tempConfigDir()
        legacyFile(configDir).writeText("{ not json")

        val manager = createConfigManager(configDir)

        assertEquals(13129, manager.getCurrentConfig().port)
        assertFalse(legacyFile(configDir).exists())
        val backup = configDir.resolve("${LegacyJsonConfigImporter.FILE_NAME}.corrupt").toFile()
        assertEquals("{ not json", backup.readText())
    }

    @Test
    fun `updateConfigDurably persists config immediately and updates memory`() {
        val manager = createConfigManager()
        manager.updateConfigDurably(
            keys = listOf("port", "enableAutoStartUp"),
            values = listOf(8888, false),
        )
        assertEquals(8888, manager.getCurrentConfig().port)
        assertFalse(manager.getCurrentConfig().enableAutoStartUp)

        val reloaded = manager.loadConfig()
        assertNotNull(reloaded)
        assertEquals(8888, reloaded.port)
        assertFalse(reloaded.enableAutoStartUp)
    }

    @Test
    fun `updateConfigDurably throws and leaves memory unchanged on save failure`() {
        val configDir = tempConfigDir()
        val manager = createConfigManager(configDir)
        val initialPort = manager.getCurrentConfig().port

        configDir.toFile().setWritable(false)
        try {
            assertFailsWith<Exception> {
                manager.updateConfigDurably(
                    keys = listOf("port"),
                    values = listOf(9999),
                )
            }
        } finally {
            configDir.toFile().setWritable(true)
        }

        assertEquals(initialPort, manager.getCurrentConfig().port)
    }

    @Test
    fun `updateConfig keeps memory unchanged on save failure`() {
        val configDir = tempConfigDir()
        val manager = createConfigManager(configDir)

        configDir.toFile().setWritable(false)
        try {
            manager.updateConfig("port", 9999)
        } finally {
            configDir.toFile().setWritable(true)
        }

        assertEquals(13129, manager.getCurrentConfig().port)
        assertFalse(settingsFile(configDir).exists())
    }
}
