package com.crosspaste.path

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class TestPathProviderMock {

    @Test
    fun testMockTestPathProvider() {
        testUseMockTestPathProvider { tempClipAppPath, tempUserHome, tempClipAppJarPath, tempUserPath ->
            assertEquals(tempClipAppPath, DesktopPathProvider.clipAppPath)
            assertEquals(tempUserHome, DesktopPathProvider.userHome)
            assertEquals(tempClipAppJarPath, DesktopPathProvider.clipAppJarPath)
            assertEquals(tempUserPath, DesktopPathProvider.clipUserPath)
        }
    }

    companion object {
        @Synchronized
        fun testUseMockTestPathProvider(testAction: (Path, Path, Path, Path) -> Unit) {
            try {
                mockkObject(DesktopPathProvider)

                // Create temporary directories
                val tempClipAppPath = Files.createTempDirectory("tempClipAppPath")
                val tempUserHome = Files.createTempDirectory("tempUserHome")
                val tempClipAppJarPath = Files.createTempDirectory("tempClipAppJarPath")
                val tempUserPath = Files.createTempDirectory("tempUserPath")

                tempClipAppPath.toFile().deleteOnExit()
                tempUserHome.toFile().deleteOnExit()
                tempClipAppJarPath.toFile().deleteOnExit()
                tempUserPath.toFile().deleteOnExit()

                every { DesktopPathProvider.clipAppPath } returns tempClipAppPath
                every { DesktopPathProvider.userHome } returns tempUserHome
                every { DesktopPathProvider.clipAppJarPath } returns tempClipAppJarPath
                every { DesktopPathProvider.clipUserPath } returns tempUserPath

                testAction(tempClipAppPath, tempUserHome, tempClipAppJarPath, tempUserPath)
            } finally {
                unmockkObject(DesktopPathProvider)
            }
        }
    }
}
