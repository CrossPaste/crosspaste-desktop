package com.clipevery.path

import org.junit.Test
import kotlin.test.assertEquals

class PathProviderTest {

    @Test
    fun testPathProvider() {
        val pathProvider = getPathProvider()

        val configPath = pathProvider.resolveUser("test.config")

        assertEquals("test.config", configPath.fileName.toString())
        assertEquals(".clipevery", configPath.parent.fileName.toString())
    }
}