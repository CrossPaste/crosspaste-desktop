package com.clipevery.path

import com.clipevery.config.FileType
import org.junit.Test
import kotlin.test.assertEquals

class PathProviderTest {

    @Test
    fun testPathProvider() {
        val pathProvider = getPathProvider()

        val configPath = pathProvider.resolve("test.config", FileType.USER)

        assertEquals("test.config", configPath.fileName.toString())
        assertEquals("Clipevery", configPath.parent.fileName.toString())
    }
}