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
            assertEquals(tempPasteAppPath, DesktopPathProvider.pasteAppPath)
            assertEquals(tempUserHome, DesktopPathProvider.userHome)
            assertEquals(tempPasteAppJarPath, DesktopPathProvider.pasteAppJarPath)
            assertEquals(tempUserPath, DesktopPathProvider.pasteUserPath)
        }
    }

    companion object {
        @Synchronized
        fun testUseMockTestPathProvider(testAction: (Path, Path, Path, Path) -> Unit) {
            try {
                mockkObject(DesktopPathProvider)

                // Create temporary directories
                val tempPasteAppPath = Files.createTempDirectory("tempPasteAppPath").toOkioPath()
                val tempUserHome = Files.createTempDirectory("tempUserHome").toOkioPath()
                val tempPasteAppJarPath = Files.createTempDirectory("tempPasteAppJarPath").toOkioPath()
                val tempUserPath = Files.createTempDirectory("tempUserPath").toOkioPath()

                tempPasteAppPath.toFile().deleteOnExit()
                tempUserHome.toFile().deleteOnExit()
                tempPasteAppJarPath.toFile().deleteOnExit()
                tempUserPath.toFile().deleteOnExit()

                every { DesktopPathProvider.pasteAppPath } returns tempPasteAppPath
                every { DesktopPathProvider.userHome } returns tempUserHome
                every { DesktopPathProvider.pasteAppJarPath } returns tempPasteAppJarPath
                every { DesktopPathProvider.pasteUserPath } returns tempUserPath

                testAction(tempPasteAppPath, tempUserHome, tempPasteAppJarPath, tempUserPath)
            } finally {
                unmockkObject(DesktopPathProvider)
            }
        }
    }
}
