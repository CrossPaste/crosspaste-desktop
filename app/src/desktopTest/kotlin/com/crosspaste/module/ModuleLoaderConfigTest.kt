package com.crosspaste.module

import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModuleLoaderConfigTest {

    @Test
    fun `getModuleItem finds item by name`() {
        val item1 = createModuleItem(name = "item1")
        val item2 = createModuleItem(name = "item2")
        val config =
            ModuleLoaderConfig(
                installPath = "/opt".toPath(),
                moduleName = "test",
                moduleItems = listOf(item1, item2),
            )
        assertEquals(item1, config.getModuleItem("item1"))
        assertEquals(item2, config.getModuleItem("item2"))
    }

    @Test
    fun `getModuleItem returns null for unknown name`() {
        val config =
            ModuleLoaderConfig(
                installPath = "/opt".toPath(),
                moduleName = "test",
                moduleItems = listOf(createModuleItem(name = "existing")),
            )
        assertNull(config.getModuleItem("nonexistent"))
    }

    @Test
    fun `ModuleItem getUrls combines each host with path`() {
        val item =
            createModuleItem(
                hosts = listOf("https://cdn1.example.com", "https://cdn2.example.com"),
                path = "/files/module.zip",
            )
        val urls = item.getUrls()
        assertEquals(2, urls.size)
        assertEquals("https://cdn1.example.com/files/module.zip", urls[0])
        assertEquals("https://cdn2.example.com/files/module.zip", urls[1])
    }

    @Test
    fun `ModuleItem getModuleFilePath resolves relative path segments from install path`() {
        val item = createModuleItem(relativePath = listOf("lib", "native", "module.so"))
        val installPath = "/opt/modules".toPath()
        val result = item.getModuleFilePath(installPath)
        assertTrue(
            result.toString().endsWith("lib/native/module.so") ||
                result.toString().endsWith("lib\\native\\module.so"),
        )
    }

    @Test
    fun `ModuleItem downloadFileName defaults to last segment of path`() {
        val item = createModuleItem(path = "/files/deep/module.zip")
        assertEquals("module.zip", item.downloadFileName)
    }

    @Test
    fun `ModuleItem custom downloadFileName overrides default`() {
        val item =
            ModuleItem(
                hosts = listOf("https://cdn.example.com"),
                path = "/files/module.zip",
                moduleItemName = "module",
                downloadFileName = "custom.zip",
                relativePath = listOf("lib"),
                sha256 = "sha",
            )
        assertEquals("custom.zip", item.downloadFileName)
    }

    private fun createModuleItem(
        name: String = "item1",
        hosts: List<String> = listOf("https://cdn.example.com"),
        path: String = "/modules/item1.zip",
        sha256: String = "abc123",
        relativePath: List<String> = listOf("lib", "item1.so"),
    ): ModuleItem =
        ModuleItem(
            hosts = hosts,
            path = path,
            moduleItemName = name,
            relativePath = relativePath,
            sha256 = sha256,
        )
}
