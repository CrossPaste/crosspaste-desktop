package com.crosspaste.config

import com.crosspaste.presist.OneFilePersist
import com.crosspaste.utils.getJsonUtils
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopSimpleConfigTest {

    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private fun createSimpleConfig(): DesktopSimpleConfig {
        val configDirPath = Files.createTempDirectory("simpleConfig").toOkioPath()
        configDirPath.toFile().deleteOnExit()
        val configPath = configDirPath.resolve("simple.json")
        return DesktopSimpleConfig(OneFilePersist(configPath))
    }

    @Test
    fun `set and get string value`() {
        val config = createSimpleConfig()
        config.setString("name", "test")
        assertEquals("test", config.getString("name"))
    }

    @Test
    fun `set and get boolean value`() {
        val config = createSimpleConfig()
        config.setBoolean("enabled", true)
        assertEquals(true, config.getBoolean("enabled"))
    }

    @Test
    fun `set and get int value`() {
        val config = createSimpleConfig()
        config.setInt("count", 42)
        assertEquals(42, config.getInt("count"))
    }

    @Test
    fun `set and get long value`() {
        val config = createSimpleConfig()
        config.setLong("bigNumber", Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, config.getLong("bigNumber"))
    }

    @Test
    fun `set and get float value`() {
        val config = createSimpleConfig()
        config.setFloat("ratio", 3.14f)
        assertEquals(3.14f, config.getFloat("ratio"))
    }

    @Test
    fun `set and get double value`() {
        val config = createSimpleConfig()
        config.setDouble("precise", 2.718281828)
        assertEquals(2.718281828, config.getDouble("precise"))
    }

    @Test
    fun `get non-existent key returns null`() {
        val config = createSimpleConfig()
        assertNull(config.getString("nonexistent"))
        assertNull(config.getBoolean("nonexistent"))
        assertNull(config.getInt("nonexistent"))
        assertNull(config.getLong("nonexistent"))
        assertNull(config.getFloat("nonexistent"))
        assertNull(config.getDouble("nonexistent"))
    }

    @Test
    fun `overwrite existing value`() {
        val config = createSimpleConfig()
        config.setString("key", "value1")
        assertEquals("value1", config.getString("key"))
        config.setString("key", "value2")
        assertEquals("value2", config.getString("key"))
    }

    @Test
    fun `remove key`() {
        val config = createSimpleConfig()
        config.setString("key", "value")
        assertEquals("value", config.getString("key"))
        config.remove("key")
        assertNull(config.getString("key"))
    }

    @Test
    fun `clear removes all values`() {
        val config = createSimpleConfig()
        config.setString("key1", "value1")
        config.setInt("key2", 42)
        config.setBoolean("key3", true)
        config.clear()
        assertNull(config.getString("key1"))
        assertNull(config.getInt("key2"))
        assertNull(config.getBoolean("key3"))
    }

    @Test
    fun `multiple keys stored independently`() {
        val config = createSimpleConfig()
        config.setString("a", "alpha")
        config.setInt("b", 2)
        config.setBoolean("c", false)
        assertEquals("alpha", config.getString("a"))
        assertEquals(2, config.getInt("b"))
        assertEquals(false, config.getBoolean("c"))
    }

    @Test
    fun `data persists across instances with same file`() {
        val configDirPath = Files.createTempDirectory("simpleConfigPersist").toOkioPath()
        configDirPath.toFile().deleteOnExit()
        val configPath = configDirPath.resolve("persist.json")

        val config1 = DesktopSimpleConfig(OneFilePersist(configPath))
        config1.setString("persistent", "data")
        config1.setInt("number", 100)

        val config2 = DesktopSimpleConfig(OneFilePersist(configPath))
        assertEquals("data", config2.getString("persistent"))
        assertEquals(100, config2.getInt("number"))
    }
}
