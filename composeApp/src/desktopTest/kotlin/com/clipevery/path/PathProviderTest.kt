package com.clipevery.path

import com.clipevery.app.AppFileType
import com.clipevery.platform.currentPlatform
import org.junit.Test
import kotlin.test.assertEquals

class PathProviderTest {

    @Test
    fun testPathProvider() {
        val pathProvider = getPathProvider()

        val configPath = pathProvider.resolve("test.config", AppFileType.USER)

        assertEquals("test.config", configPath.fileName.toString())
        val currentPlatform = currentPlatform()
        if (currentPlatform.isMacos()) {
            assertEquals("Clipevery", configPath.parent.fileName.toString())
        } else if (currentPlatform.isWindows()) {
            assertEquals(".clipevery", configPath.parent.fileName.toString())
        }
    }
}