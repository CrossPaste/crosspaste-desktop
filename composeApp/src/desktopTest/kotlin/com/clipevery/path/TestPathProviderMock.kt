package com.clipevery.path

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
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

fun testUseMockTestPathProvider(testAction: (Path, Path, Path, Path) -> Unit) {
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

        testAction(tempClipAppPath, tempUserHome, tempClipAppJarPath, tempUserPath)
    } finally {
        unmockkAll()
    }
}
