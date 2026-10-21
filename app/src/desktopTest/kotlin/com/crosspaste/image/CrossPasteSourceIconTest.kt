package com.crosspaste.image

import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppName
import com.crosspaste.path.UserDataPathProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrossPasteSourceIconTest {

    private val fileSystem = FileSystem.SYSTEM
    private val tempDir: Path = Files.createTempDirectory("icon_test").toOkioPath()
    private val iconPath = tempDir.resolve("$AppName.png")
    private val tempIconPath = tempDir.resolve("$AppName.png.tmp")

    private val appInfo =
        mockk<AppInfo> {
            every { appInstanceId } returns "test-instance-id"
        }

    private val userDataPathProvider =
        mockk<UserDataPathProvider> {
            every { resolveIconPath("test-instance-id", AppName) } returns iconPath
        }

    @AfterTest
    fun cleanup() {
        fileSystem.deleteRecursively(tempDir)
    }

    @Test
    fun `installCrossPasteSourceIcon installs icon when it does not exist`() =
        runTest {
            assertFalse(fileSystem.exists(iconPath))

            installCrossPasteSourceIcon(appInfo, userDataPathProvider)

            assertTrue(fileSystem.exists(iconPath))
            assertTrue(fileSystem.metadata(iconPath).size ?: 0L > 1000L)
            assertFalse(fileSystem.exists(tempIconPath))
        }

    @Test
    fun `installCrossPasteSourceIcon skips writing when icon already exists with identical size`() =
        runTest {
            installCrossPasteSourceIcon(appInfo, userDataPathProvider)
            val bundledSize = fileSystem.metadata(iconPath).size!!.toInt()
            // Same size as the bundled icon but different bytes: only a skipped write leaves them intact
            val placeholder = ByteArray(bundledSize)
            fileSystem.write(iconPath) { write(placeholder) }

            installCrossPasteSourceIcon(appInfo, userDataPathProvider)

            assertContentEquals(placeholder, fileSystem.read(iconPath) { readByteArray() })
            assertFalse(fileSystem.exists(tempIconPath))
        }

    @Test
    fun `installCrossPasteSourceIcon overwrites icon when existing file size differs`() =
        runTest {
            // Write a dummy file with different size
            fileSystem.write(iconPath) { writeUtf8("outdated icon") }
            val dummySize = fileSystem.metadata(iconPath).size ?: 0L

            installCrossPasteSourceIcon(appInfo, userDataPathProvider)

            val newSize = fileSystem.metadata(iconPath).size ?: 0L
            assertTrue(newSize > dummySize)
            assertFalse(fileSystem.exists(tempIconPath))
        }
}
