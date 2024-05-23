package com.clipevery.path

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals

class TestPathProviderMock {

    @Test
    fun testMockTestPathProvider() {
        try {
            mockkObject(TestPathProvider)

            // Create temporary directories
            val tempClipAppPath = Files.createTempDirectory("tempClipAppPath")
            val tempUserHome = Files.createTempDirectory("tempUserHome")
            val tempClipAppJarPath = Files.createTempDirectory("tempClipAppJarPath")
            val tempUserPath = Files.createTempDirectory("tempUserPath")

            tempClipAppPath.toFile().deleteOnExit()
            tempUserHome.toFile().deleteOnExit()
            tempClipAppJarPath.toFile().deleteOnExit()
            tempUserPath.toFile().deleteOnExit()

            every { TestPathProvider.needMockClipAppPath() } returns tempClipAppPath
            every { TestPathProvider.needMockUserHome() } returns tempUserHome
            every { TestPathProvider.needMockClipAppJarPath() } returns tempClipAppJarPath
            every { TestPathProvider.needMockUserPath() } returns tempUserPath

            val pathProvider = DesktopPathProvider

            assertEquals(tempClipAppPath, pathProvider.clipAppPath)
            assertEquals(tempUserHome, pathProvider.userHome)
            assertEquals(tempClipAppJarPath, pathProvider.clipAppJarPath)
            assertEquals(tempUserPath, pathProvider.clipUserPath)
        } finally {
            unmockkAll()
        }
    }
}
