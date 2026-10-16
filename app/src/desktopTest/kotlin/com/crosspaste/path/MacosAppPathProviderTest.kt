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

class MacosAppPathProviderTest {
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

    @Test
    fun `launcher resource path is normalized before resolving the app bundle`() {
        val bundle = tempDir.toOkioPath().resolve("CrossPaste Test.app")
        every { DesktopSystemProperty.getOption("compose.application.resources.dir") } returns
            "$bundle/Contents/app/../Resources"

        assertPaths(MacosAppPathProvider(), bundle, bundle.resolve("Contents/Resources"))
    }

    @Test
    fun `canonical resource directory preserves bundle and runtime locations`() {
        val bundle = tempDir.toOkioPath().resolve("CrossPaste.app")
        val resources = bundle.resolve("Contents/Resources")
        every { DesktopSystemProperty.getOption("compose.application.resources.dir") } returns resources.toString()

        assertPaths(MacosAppPathProvider(), bundle, resources)
    }

    @Test
    fun `canonical app library directory preserves bundle and runtime locations`() {
        val bundle = tempDir.toOkioPath().resolve("CrossPaste.app")
        val libraries = bundle.resolve("Contents/app")
        every { DesktopSystemProperty.getOption("skiko.library.path") } returns libraries.toString()

        assertPaths(MacosAppPathProvider(), bundle, libraries)
    }

    @Test
    fun `native library path is normalized before resolving the app bundle`() {
        val bundle = tempDir.toOkioPath().resolve("CrossPaste Test.app")
        every { DesktopSystemProperty.getOption("skiko.library.path") } returns "$bundle/Contents/lib/../app"

        assertPaths(MacosAppPathProvider(), bundle, bundle.resolve("Contents/app"))
    }

    @Test
    fun `resource directory retains precedence over the native library directory`() {
        val bundle = tempDir.toOkioPath().resolve("CrossPaste.app")
        val resources = bundle.resolve("Contents/Resources")
        every { DesktopSystemProperty.getOption("compose.application.resources.dir") } returns resources.toString()
        every { DesktopSystemProperty.getOption("skiko.library.path") } returns
            tempDir.toOkioPath().resolve("another.app/Contents/app").toString()

        assertPaths(MacosAppPathProvider(), bundle, resources)
    }

    private fun assertPaths(
        provider: MacosAppPathProvider,
        bundle: Path,
        libraries: Path,
    ) {
        assertEquals(bundle, provider.pasteAppPath)
        assertEquals(libraries, provider.pasteAppJarPath)
        assertEquals(bundle.resolve("Contents/runtime/Contents/Home/lib"), provider.pasteAppExePath)
        assertEquals(bundle.resolve("Contents/bin/start.sh"), provider.pasteAppPath.resolve("Contents/bin/start.sh"))
    }
}
