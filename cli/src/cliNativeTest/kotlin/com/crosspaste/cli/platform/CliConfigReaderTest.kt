package com.crosspaste.cli.platform

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class StaticPathProvider(
    private val dir: Path,
) : NativePlatformPathProvider {
    override fun getDefaultUserDataPath(): Path = dir
}

class CliConfigReaderTest {

    private fun tempDir(): Path {
        val dir =
            FileSystem.SYSTEM_TEMPORARY_DIRECTORY
                .resolve("cli-config-reader-test-${Random.nextBits(31)}")
        FileSystem.SYSTEM.createDirectories(dir)
        return dir
    }

    /**
     * A `PreferenceMap` as the app's DataStore (protobuf-lite on the JVM) writes
     * it, encoded by hand so the test does not depend on our own encoder:
     * `{"useDefaultStoragePath": false, "storagePath": "/data/x"}`.
     */
    private fun settingsBytes(): ByteArray {
        val useDefault =
            byteArrayOf(0x0A, 0x15) + "useDefaultStoragePath".encodeToByteArray() +
                byteArrayOf(0x12, 0x02, 0x08, 0x00) // Value { boolean = false }
        val storagePath =
            byteArrayOf(0x0A, 0x0B) + "storagePath".encodeToByteArray() +
                byteArrayOf(0x12, 0x09, 0x2A, 0x07) + "/data/x".encodeToByteArray() // Value { string = "/data/x" }
        return byteArrayOf(0x0A, useDefault.size.toByte()) + useDefault +
            byteArrayOf(0x0A, storagePath.size.toByte()) + storagePath
    }

    @Test
    fun `reads the storage location from the DataStore settings file`() {
        val dir = tempDir()
        FileSystem.SYSTEM.write(dir.resolve(CliConfigReader.SETTINGS_FILE_NAME)) { write(settingsBytes()) }
        // A stale legacy document must not win over the settings file.
        FileSystem.SYSTEM.write(dir.resolve(CliConfigReader.LEGACY_CONFIG_FILE_NAME)) {
            writeUtf8("""{"useDefaultStoragePath":true,"storagePath":""}""")
        }

        val reader = CliConfigReader(StaticPathProvider(dir))

        assertEquals(CliAppConfig(useDefaultStoragePath = false, storagePath = "/data/x"), reader.readConfig())
        assertEquals("/data/x".toPath(), reader.resolveUserDataPath())
    }

    @Test
    fun `decodes every preference value type`() {
        val preferences = decodePreferences(settingsBytes())
        assertEquals(false, preferences.getValue("useDefaultStoragePath").boolean)
        assertEquals("/data/x", preferences.getValue("storagePath").string)
        assertEquals(setOf("useDefaultStoragePath", "storagePath"), preferences.keys)
    }

    @Test
    fun `falls back to the legacy json document`() {
        val dir = tempDir()
        FileSystem.SYSTEM.write(dir.resolve(CliConfigReader.LEGACY_CONFIG_FILE_NAME)) {
            writeUtf8("""{"useDefaultStoragePath":false,"storagePath":"/legacy","port":1}""")
        }

        val config = CliConfigReader(StaticPathProvider(dir)).readConfig()

        assertFalse(config.useDefaultStoragePath)
        assertEquals("/legacy", config.storagePath)
    }

    @Test
    fun `defaults when no config exists or the settings file is unreadable`() {
        val dir = tempDir()
        assertEquals(CliAppConfig(), CliConfigReader(StaticPathProvider(dir)).readConfig())

        FileSystem.SYSTEM.write(dir.resolve(CliConfigReader.SETTINGS_FILE_NAME)) { write(byteArrayOf(0x0A, 0x7F)) }
        val reader = CliConfigReader(StaticPathProvider(dir))
        assertTrue(reader.readConfig().useDefaultStoragePath)
        assertEquals(dir, reader.resolveUserDataPath())
    }
}
