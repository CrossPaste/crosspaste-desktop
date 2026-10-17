package com.crosspaste.path

import com.crosspaste.utils.DesktopSystemProperty
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PackagedAppPathProviderTest {
    @TempDir
    lateinit var tempDir: File

    @BeforeTest
    fun setup() {
        mockkObject(DesktopSystemProperty)
        every { DesktopSystemProperty.get("user.home") } returns tempDir.absolutePath
        every { DesktopSystemProperty.getOption("compose.application.resources.dir") } returns null
        every { DesktopSystemProperty.getOption("skiko.library.path") } returns null
    }

    @AfterTest
    fun cleanup() {
        unmockkObject(DesktopSystemProperty)
    }

    private fun givenResourcesDir(path: String) {
        every { DesktopSystemProperty.getOption("compose.application.resources.dir") } returns path
    }

    @Test
    fun `macos resource path is normalized before resolving the app bundle`() {
        val bundle = tempDir.toOkioPath().resolve("CrossPaste Test.app")
        givenResourcesDir("$bundle/Contents/app/../Resources")

        assertMacosPaths(MacosAppPathProvider(), bundle)
    }

    @Test
    fun `macos canonical resource directory preserves bundle and runtime locations`() {
        val bundle = tempDir.toOkioPath().resolve("CrossPaste.app")
        givenResourcesDir(bundle.resolve("Contents/Resources").toString())

        assertMacosPaths(MacosAppPathProvider(), bundle)
    }

    @Test
    fun `windows resource path is normalized before resolving the install root`() {
        val root = tempDir.toOkioPath().resolve("CrossPaste")
        givenResourcesDir("$root/app/runtime/..")

        val provider = WindowsAppPathProvider()

        assertEquals(root, provider.pasteAppPath)
        assertEquals(root.resolve("app"), provider.pasteAppJarPath)
        assertEquals(root.resolve("bin"), provider.pasteAppExePath)
    }

    @Test
    fun `linux resource path is normalized before resolving the install root`() {
        val root = tempDir.toOkioPath().resolve("crosspaste")
        givenResourcesDir("$root/lib/app/../app")

        val provider = LinuxAppPathProvider()

        assertEquals(root, provider.pasteAppPath)
        assertEquals(root.resolve("lib/app"), provider.pasteAppJarPath)
        assertEquals(root.resolve("lib/runtime/lib"), provider.pasteAppExePath)
    }

    @Test
    fun `native library path is not used to derive the install root`() {
        every { DesktopSystemProperty.getOption("skiko.library.path") } returns
            tempDir.toOkioPath().resolve("CrossPaste.app/Contents/Resources/../runtime/Contents/Home/lib").toString()

        assertFailsWith<IllegalStateException> { MacosAppPathProvider() }
        assertFailsWith<IllegalStateException> { WindowsAppPathProvider() }
        assertFailsWith<IllegalStateException> { LinuxAppPathProvider() }
    }

    private fun assertMacosPaths(
        provider: MacosAppPathProvider,
        bundle: Path,
    ) {
        assertEquals(bundle, provider.pasteAppPath)
        assertEquals(bundle.resolve("Contents/Resources"), provider.pasteAppJarPath)
        assertEquals(bundle.resolve("Contents/runtime/Contents/Home/lib"), provider.pasteAppExePath)
    }
}
