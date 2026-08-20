package com.crosspaste.mouse

import okio.Path
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MouseDaemonBinaryLocatorTest {

    private fun createTempDir(): Path {
        val dir = Files.createTempDirectory("mouseLocator")
        dir.toFile().deleteOnExit()
        return dir.toOkioPath()
    }

    private fun createFile(path: Path): Path {
        val nioPath = path.toNioPath()
        Files.createDirectories(nioPath.parent)
        Files.createFile(nioPath)
        return path
    }

    @Test
    fun `dev binary path wins over bundled binary`() {
        val dir = createTempDir()
        val devBinary = createFile(dir.resolve("local").resolve("crosspaste-mouse"))
        val bundledDir = dir.resolve("resources")
        createFile(bundledDir.resolve("crosspaste-mouse"))

        val locator =
            MouseDaemonBinaryLocator(
                devBinaryPath = devBinary.toString(),
                bundledDir = bundledDir,
                isWindows = false,
            )

        assertEquals(devBinary, locator.locate())
    }

    @Test
    fun `missing dev binary falls through to bundled binary`() {
        val dir = createTempDir()
        val bundledDir = dir.resolve("resources")
        val bundled = createFile(bundledDir.resolve("crosspaste-mouse"))

        val locator =
            MouseDaemonBinaryLocator(
                devBinaryPath = dir.resolve("missing").toString(),
                bundledDir = bundledDir,
                isWindows = false,
            )

        assertEquals(bundled, locator.locate())
    }

    @Test
    fun `windows resolves exe bundled binary name`() {
        val dir = createTempDir()
        val bundledDir = dir.resolve("resources")
        val exe = createFile(bundledDir.resolve("crosspaste-mouse.exe"))

        val locator =
            MouseDaemonBinaryLocator(
                devBinaryPath = null,
                bundledDir = bundledDir,
                isWindows = true,
            )

        assertEquals(exe, locator.locate())
    }

    @Test
    fun `returns null when no candidate exists`() {
        val dir = createTempDir()

        val locator =
            MouseDaemonBinaryLocator(
                devBinaryPath = dir.resolve("missing").toString(),
                bundledDir = dir.resolve("missing-resources"),
                isWindows = false,
            )

        assertNull(locator.locate())
    }
}
