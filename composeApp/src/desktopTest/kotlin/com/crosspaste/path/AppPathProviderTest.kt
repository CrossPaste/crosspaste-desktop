package com.crosspaste.path

import com.crosspaste.app.AppFileType
import com.crosspaste.path.TestAppPathProviderMock.useMockAppPathProvider
import com.crosspaste.platform.currentPlatform
import com.crosspaste.utils.noOptionParent
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class AppPathProviderTest {

    @Test
    fun testMockProvider() {
        useMockAppPathProvider { tempPasteAppPath, tempUserHome, tempPasteAppJarPath, tempUserPath ->
            assertEquals(tempPasteAppPath, DesktopAppPathProvider.pasteAppPath)
            assertEquals(tempUserHome, DesktopAppPathProvider.userHome)
            assertEquals(tempPasteAppJarPath, DesktopAppPathProvider.pasteAppJarPath)
            assertEquals(tempUserPath, DesktopAppPathProvider.pasteUserPath)
        }
    }

    @Test
    @Ignore
    fun testAppPathProvider() {
        val configPath = DesktopAppPathProvider.resolve("test.config", AppFileType.USER)

        assertEquals("test.config", configPath.name)
        val currentPlatform = currentPlatform()
        if (currentPlatform.isMacos()) {
            assertEquals("CrossPaste", configPath.noOptionParent.name)
        } else if (currentPlatform.isWindows()) {
            assertEquals(".crosspaste", configPath.noOptionParent.name)
        }
    }
}
