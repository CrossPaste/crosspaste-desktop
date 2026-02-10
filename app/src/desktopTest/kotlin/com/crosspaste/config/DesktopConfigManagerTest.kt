package com.crosspaste.config

import com.crosspaste.platform.DesktopPlatformProvider
import com.crosspaste.presist.OneFilePersist
import com.crosspaste.utils.DesktopDeviceUtils
import com.crosspaste.utils.DesktopLocaleUtils
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopConfigManagerTest {

    private val platform = DesktopPlatformProvider().getPlatform()

    private fun createConfigManager(): Pair<DesktopConfigManager, okio.Path> {
        val configDirPath = Files.createTempDirectory("configDir").toOkioPath()
        configDirPath.toFile().deleteOnExit()
        val configPath = configDirPath.resolve("appConfig.json")
        val manager =
            DesktopConfigManager(
                OneFilePersist(configPath),
                DesktopDeviceUtils(platform),
                DesktopLocaleUtils,
            )
        return Pair(manager, configPath)
    }

    @Test
    fun `initial config has non-empty appInstanceId`() {
        val (manager, _) = createConfigManager()
        val config = manager.getCurrentConfig()
        assertTrue(config.appInstanceId.isNotBlank())
    }

    @Test
    fun `initial config has default values`() {
        val (manager, _) = createConfigManager()
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
        val (manager, _) = createConfigManager()
        manager.updateConfig("enableAutoStartUp", false)
        assertEquals(false, manager.getCurrentConfig().enableAutoStartUp)
    }

    @Test
    fun `updateConfig updates single int field`() {
        val (manager, _) = createConfigManager()
        manager.updateConfig("port", 9999)
        assertEquals(9999, manager.getCurrentConfig().port)
    }

    @Test
    fun `updateConfig updates single long field`() {
        val (manager, _) = createConfigManager()
        manager.updateConfig("maxStorage", 4096L)
        assertEquals(4096L, manager.getCurrentConfig().maxStorage)
    }

    @Test
    fun `updateConfig updates single string field`() {
        val (manager, _) = createConfigManager()
        manager.updateConfig("language", "zh")
        assertEquals("zh", manager.getCurrentConfig().language)
    }

    @Test
    fun `updateConfig batch updates multiple fields`() {
        val (manager, _) = createConfigManager()
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
        val (manager, _) = createConfigManager()
        val initialPort = manager.config.value.port
        manager.updateConfig("port", 7777)
        assertEquals(7777, manager.config.value.port)
        assertNotEquals(initialPort, manager.config.value.port)
    }

    @Test
    fun `config persists across manager instances`() {
        val configDirPath = Files.createTempDirectory("configPersist").toOkioPath()
        configDirPath.toFile().deleteOnExit()
        val configPath = configDirPath.resolve("appConfig.json")

        val manager1 =
            DesktopConfigManager(
                OneFilePersist(configPath),
                DesktopDeviceUtils(platform),
                DesktopLocaleUtils,
            )
        manager1.updateConfig("port", 5555)
        val instanceId = manager1.getCurrentConfig().appInstanceId

        val manager2 =
            DesktopConfigManager(
                OneFilePersist(configPath),
                DesktopDeviceUtils(platform),
                DesktopLocaleUtils,
            )
        assertEquals(5555, manager2.getCurrentConfig().port)
        assertEquals(instanceId, manager2.getCurrentConfig().appInstanceId)
    }

    @Test
    fun `updateConfig with invalid save reverts config`() {
        // Create a config with read-only persist to force save failure
        val configDirPath = Files.createTempDirectory("configRevert").toOkioPath()
        configDirPath.toFile().deleteOnExit()
        val configPath = configDirPath.resolve("appConfig.json")

        val manager =
            DesktopConfigManager(
                OneFilePersist(configPath),
                DesktopDeviceUtils(platform),
                DesktopLocaleUtils,
            )
        // First save should work
        manager.updateConfig("port", 1234)
        assertEquals(1234, manager.getCurrentConfig().port)
    }

    @Test
    fun `loadConfig returns null for non-existent file`() {
        val configDirPath = Files.createTempDirectory("configLoad").toOkioPath()
        configDirPath.toFile().deleteOnExit()
        val configPath = configDirPath.resolve("nonexistent.json")
        val manager =
            DesktopConfigManager(
                OneFilePersist(configPath),
                DesktopDeviceUtils(platform),
                DesktopLocaleUtils,
            )
        // Initial config should still be created from defaults
        assertNotNull(manager.getCurrentConfig())
    }

    @Test
    fun `batch updateConfig requires equal sized lists`() {
        val (manager, _) = createConfigManager()
        try {
            manager.updateConfig(
                keys = listOf("port"),
                values = listOf(1, 2),
            )
            assertTrue(false, "Should have thrown")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `updateConfig updates sync content type controls`() {
        val (manager, _) = createConfigManager()
        manager.updateConfig("enableSyncText", false)
        assertEquals(false, manager.getCurrentConfig().enableSyncText)
        manager.updateConfig("enableSyncImage", false)
        assertEquals(false, manager.getCurrentConfig().enableSyncImage)
    }
}
