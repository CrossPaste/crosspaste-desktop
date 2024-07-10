package com.crosspaste.path

import com.crosspaste.app.AppFileType
import com.crosspaste.platform.currentPlatform
import com.crosspaste.utils.noOptionParent
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class PathProviderTest {

    @Test
    @Ignore
    fun testPathProvider() {
        val pathProvider = DesktopPathProvider

        val configPath = pathProvider.resolve("test.config", AppFileType.USER)

        assertEquals("test.config", configPath.name)
        val currentPlatform = currentPlatform()
        if (currentPlatform.isMacos()) {
            assertEquals("CrossPaste", configPath.noOptionParent.name)
        } else if (currentPlatform.isWindows()) {
            assertEquals(".crosspaste", configPath.noOptionParent.name)
        }
    }
}
