package com.crosspaste.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopAppConfigTest {

    private fun createDefaultConfig(): DesktopAppConfig =
        DesktopAppConfig(
            appInstanceId = "test-instance",
            language = "en",
        )

    @Test
    fun `default config has expected values`() {
        val config = createDefaultConfig()
        assertEquals("test-instance", config.appInstanceId)
        assertEquals("en", config.language)
        assertEquals("", config.font)
        assertTrue(config.enableAutoStartUp)
        assertFalse(config.enableDebugMode)
        assertTrue(config.isFollowSystemTheme)
        assertFalse(config.isDarkTheme)
        assertEquals(13129, config.port)
        assertFalse(config.enableEncryptSync)
        assertTrue(config.enableExpirationCleanup)
        assertEquals(6, config.imageCleanTimeIndex)
        assertEquals(6, config.fileCleanTimeIndex)
        assertTrue(config.enableThresholdCleanup)
        assertEquals(2048L, config.maxStorage)
        assertEquals(20, config.cleanupPercentage)
        assertTrue(config.enableDiscovery)
        assertEquals("[]", config.blacklist)
        assertTrue(config.enablePasteboardListening)
        assertTrue(config.showTutorial)
        assertEquals(32L, config.maxBackupFileSize)
        assertTrue(config.enabledSyncFileSizeLimit)
        assertEquals(512L, config.maxSyncFileSize)
        assertTrue(config.useDefaultStoragePath)
        assertEquals("", config.storagePath)
        assertTrue(config.enableSoundEffect)
        assertFalse(config.legacySoftwareCompatibility)
        assertTrue(config.pastePrimaryTypeOnly)
    }

    @Test
    fun `copy with string key updates language`() {
        val config: AppConfig = createDefaultConfig()
        val updated = config.copy("language", "zh")
        assertEquals("zh", updated.language)
        assertEquals(config.port, updated.port)
    }

    @Test
    fun `copy with boolean key updates enableEncryptSync`() {
        val config: AppConfig = createDefaultConfig()
        val updated = config.copy("enableEncryptSync", true)
        assertTrue(updated.enableEncryptSync)
    }

    @Test
    fun `copy with int key updates port`() {
        val config: AppConfig = createDefaultConfig()
        val updated = config.copy("port", 8080)
        assertEquals(8080, updated.port)
    }

    @Test
    fun `copy with long key updates maxStorage`() {
        val config: AppConfig = createDefaultConfig()
        val updated = config.copy("maxStorage", 4096L)
        assertEquals(4096L, updated.maxStorage)
    }

    @Test
    fun `copy preserves appInstanceId`() {
        val config: AppConfig = createDefaultConfig()
        val updated = config.copy("language", "ja")
        assertEquals("test-instance", updated.appInstanceId)
    }

    @Test
    fun `copy with unknown key does not change config`() {
        val config: AppConfig = createDefaultConfig()
        val updated = config.copy("unknownKey", "unknownValue")
        assertEquals(config.language, updated.language)
        assertEquals(config.port, updated.port)
        assertEquals(config.enableEncryptSync, updated.enableEncryptSync)
    }

    @Test
    fun `copy updates all sync content type controls`() {
        val config: AppConfig = createDefaultConfig()
        var updated = config.copy("enableSyncText", false)
        assertFalse(updated.enableSyncText)
        updated = config.copy("enableSyncUrl", false)
        assertFalse(updated.enableSyncUrl)
        updated = config.copy("enableSyncHtml", false)
        assertFalse(updated.enableSyncHtml)
        updated = config.copy("enableSyncRtf", false)
        assertFalse(updated.enableSyncRtf)
        updated = config.copy("enableSyncImage", false)
        assertFalse(updated.enableSyncImage)
        updated = config.copy("enableSyncFile", false)
        assertFalse(updated.enableSyncFile)
        updated = config.copy("enableSyncColor", false)
        assertFalse(updated.enableSyncColor)
    }

    @Test
    fun `copy chain updates multiple fields`() {
        val config: AppConfig = createDefaultConfig()
        val updated =
            config
                .copy("port", 9999)
                .copy("language", "de")
                .copy("enableEncryptSync", true)
        assertEquals(9999, updated.port)
        assertEquals("de", updated.language)
        assertTrue(updated.enableEncryptSync)
    }
}
