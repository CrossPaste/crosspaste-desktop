package com.crosspaste.path

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import okio.Path
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class TestPathProviderMock {

    @Test
    fun testMockTestPathProvider() {
        testUseMockTestPathProvider { tempPasteAppPath, tempUserHome, tempPasteAppJarPath, tempUserPath ->
            assertEquals(tempPasteAppPath, DesktopAppPathProvider.pasteAppPath)
            assertEquals(tempUserHome, DesktopAppPathProvider.userHome)
            assertEquals(tempPasteAppJarPath, DesktopAppPathProvider.pasteAppJarPath)
            assertEquals(tempUserPath, DesktopAppPathProvider.pasteUserPath)
        }
    }

    companion object {
        @Synchronized
        fun testUseMockTestPathProvider(testAction: (Path, Path, Path, Path) -> Unit) {
            try {
                mockkObject(DesktopAppPathProvider)

                // Create temporary directories
                val tempPasteAppPath = Files.createTempDirectory("tempPasteAppPath").toOkioPath()
                val tempUserHome = Files.createTempDirectory("tempUserHome").toOkioPath()
                val tempPasteAppJarPath = Files.createTempDirectory("tempPasteAppJarPath").toOkioPath()
                val tempUserPath = Files.createTempDirectory("tempUserPath").toOkioPath()

                tempPasteAppPath.toFile().deleteOnExit()
                tempUserHome.toFile().deleteOnExit()
                tempPasteAppJarPath.toFile().deleteOnExit()
                tempUserPath.toFile().deleteOnExit()

                every { DesktopAppPathProvider.pasteAppPath } returns tempPasteAppPath
                every { DesktopAppPathProvider.userHome } returns tempUserHome
                every { DesktopAppPathProvider.pasteAppJarPath } returns tempPasteAppJarPath
                every { DesktopAppPathProvider.pasteUserPath } returns tempUserPath

                testAction(tempPasteAppPath, tempUserHome, tempPasteAppJarPath, tempUserPath)
            } finally {
                unmockkObject(DesktopAppPathProvider)
            }
        }
    }
}
