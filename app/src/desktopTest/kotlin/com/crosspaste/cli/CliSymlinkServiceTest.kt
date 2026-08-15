package com.crosspaste.cli

import com.crosspaste.app.AppFileType
import com.crosspaste.path.AppPathProvider
import com.crosspaste.platform.Platform
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime
import java.nio.file.Path as NioPath

class CliSymlinkServiceTest {

    private lateinit var tempDir: NioPath
    private lateinit var payloadBinDir: NioPath
    private lateinit var cliBinary: NioPath
    private lateinit var linkDir: NioPath
    private lateinit var linkPath: NioPath

    @BeforeTest
    fun setUp() {
        tempDir = createTempDirectory("cli-symlink-test")
        // Mirrors Contents/Resources/bin/crosspaste-cli under a fake jar path
        payloadBinDir = tempDir.resolve("Resources").resolve("bin")
        Files.createDirectories(payloadBinDir)
        cliBinary = payloadBinDir.resolve("crosspaste-cli")
        Files.write(cliBinary, byteArrayOf(1))
        linkDir = tempDir.resolve("usr-local-bin")
        Files.createDirectories(linkDir)
        linkPath = linkDir.resolve("crosspaste")
    }

    @AfterTest
    fun tearDown() {
        // Restore permissions the escalation test may have dropped
        runCatching {
            Files.setPosixFilePermissions(linkDir, PosixFilePermissions.fromString("rwxr-xr-x"))
        }
        tempDir.toFile().deleteRecursively()
    }

    private fun fakeAppPathProvider(jarDir: NioPath): AppPathProvider {
        val jarPath = jarDir.toOkioPath()
        val root = tempDir.toOkioPath()
        return object : AppPathProvider {
            override val userHome: Path = root
            override val pasteAppPath: Path = root
            override val pasteAppJarPath: Path = jarPath
            override val pasteAppExePath: Path = root
            override val pasteUserPath: Path = root

            override fun resolve(
                fileName: String?,
                appFileType: AppFileType,
            ): Path = root
        }
    }

    private fun createService(
        supported: Boolean = true,
        jarDir: NioPath = tempDir.resolve("Resources"),
        escalatedInstall: ((Path, Path) -> CliInstallResult)? = null,
    ): CliSymlinkService =
        CliSymlinkService(
            appPathProvider = fakeAppPathProvider(jarDir),
            // The platform check is bypassed via supportedOverride; any value works
            platform = Platform(name = Platform.MACOS, arch = "arm64", bitMode = 64, version = "14.0"),
            linkPath = linkPath.toOkioPath(),
            supportedOverride = supported,
            escalatedInstall = escalatedInstall,
        )

    private suspend fun refreshedState(service: CliSymlinkService): CliSymlinkState {
        service.refresh()
        return service.state.value
    }

    @Test
    fun `not supported when override is false`() =
        runTest {
            assertEquals(CliSymlinkState.NOT_SUPPORTED, refreshedState(createService(supported = false)))
        }

    @Test
    fun `not supported when cli binary is absent`() =
        runTest {
            Files.delete(cliBinary)
            assertEquals(CliSymlinkState.NOT_SUPPORTED, refreshedState(createService()))
        }

    @Test
    fun `not installed when link is missing`() =
        runTest {
            assertEquals(CliSymlinkState.NOT_INSTALLED, refreshedState(createService()))
        }

    @Test
    fun `installed when link points at the bundled cli`() =
        runTest {
            Files.createSymbolicLink(linkPath, cliBinary)
            assertEquals(CliSymlinkState.INSTALLED, refreshedState(createService()))
        }

    @Test
    fun `needs repair when link points elsewhere`() =
        runTest {
            val other = tempDir.resolve("other-cli")
            Files.write(other, byteArrayOf(1))
            Files.createSymbolicLink(linkPath, other)
            assertEquals(CliSymlinkState.NEEDS_REPAIR, refreshedState(createService()))
        }

    @Test
    fun `needs repair when link is dangling`() =
        runTest {
            Files.createSymbolicLink(linkPath, tempDir.resolve("gone"))
            assertEquals(CliSymlinkState.NEEDS_REPAIR, refreshedState(createService()))
        }

    @Test
    fun `conflict when a regular file occupies the link path`() =
        runTest {
            Files.write(linkPath, byteArrayOf(1))
            assertEquals(CliSymlinkState.CONFLICT, refreshedState(createService()))
        }

    @Test
    fun `conflict when a directory occupies the link path`() =
        runTest {
            Files.createDirectories(linkPath)
            assertEquals(CliSymlinkState.CONFLICT, refreshedState(createService()))
        }

    @Test
    fun `translocated when the bundle runs from an AppTranslocation mount`() =
        runTest {
            val translocatedJar = tempDir.resolve("AppTranslocation").resolve("Resources")
            val binDir = translocatedJar.resolve("bin")
            Files.createDirectories(binDir)
            Files.write(binDir.resolve("crosspaste-cli"), byteArrayOf(1))
            assertEquals(
                CliSymlinkState.TRANSLOCATED,
                refreshedState(createService(jarDir = translocatedJar)),
            )
        }

    @Test
    fun `install refuses to touch a conflicting regular file`() =
        runTest {
            Files.write(linkPath, byteArrayOf(7))
            val service = createService()
            assertEquals(CliInstallResult.FAILURE, service.install())
            assertEquals(CliSymlinkState.CONFLICT, service.state.value)
            // The foreign file is untouched
            assertEquals(7, Files.readAllBytes(linkPath)[0].toInt())
        }

    @Test
    fun `install refuses when translocated`() =
        runTest {
            val translocatedJar = tempDir.resolve("AppTranslocation").resolve("Resources")
            val binDir = translocatedJar.resolve("bin")
            Files.createDirectories(binDir)
            Files.write(binDir.resolve("crosspaste-cli"), byteArrayOf(1))
            val service = createService(jarDir = translocatedJar)
            assertEquals(CliInstallResult.FAILURE, service.install())
            assertEquals(CliSymlinkState.TRANSLOCATED, service.state.value)
        }

    @Test
    fun `install fails when escalation claims success but the link is still wrong`() =
        runTest {
            Files.setPosixFilePermissions(linkDir, PosixFilePermissions.fromString("r-xr-xr-x"))
            // Escalation lies: reports SUCCESS without creating anything
            val service = createService(escalatedInstall = { _, _ -> CliInstallResult.SUCCESS })
            assertEquals(CliInstallResult.FAILURE, service.install())
            assertEquals(CliSymlinkState.NOT_INSTALLED, service.state.value)
        }

    @Test
    fun `install creates the symlink directly`() =
        runTest {
            val service = createService()
            assertEquals(CliInstallResult.SUCCESS, service.install())
            assertEquals(CliSymlinkState.INSTALLED, service.state.value)
            assertEquals(cliBinary, Files.readSymbolicLink(linkPath))
        }

    @Test
    fun `install repairs a link pointing elsewhere`() =
        runTest {
            val other = tempDir.resolve("other-cli")
            Files.write(other, byteArrayOf(1))
            Files.createSymbolicLink(linkPath, other)
            val service = createService()
            assertEquals(CliInstallResult.SUCCESS, service.install())
            assertEquals(CliSymlinkState.INSTALLED, service.state.value)
        }

    @Test
    fun `install falls back to escalation when the link dir is not writable`() =
        runTest {
            Files.setPosixFilePermissions(linkDir, PosixFilePermissions.fromString("r-xr-xr-x"))
            var escalated = false
            val service =
                createService(escalatedInstall = { _, _ ->
                    escalated = true
                    CliInstallResult.CANCELLED
                })
            assertEquals(CliInstallResult.CANCELLED, service.install())
            assertEquals(true, escalated)
        }

    @Test
    fun `install fails when not supported`() =
        runTest {
            assertEquals(CliInstallResult.FAILURE, createService(supported = false).install())
        }

    @Test
    fun `classify osascript results`() {
        assertEquals(CliInstallResult.SUCCESS, classifyOsascriptResult(0, ""))
        assertEquals(
            CliInstallResult.CANCELLED,
            classifyOsascriptResult(1, "execution error: User canceled. (-128)"),
        )
        assertEquals(CliInstallResult.FAILURE, classifyOsascriptResult(1, "some error (-12800)"))
        assertEquals(CliInstallResult.FAILURE, classifyOsascriptResult(1, "execution error: broken"))
    }

    private fun renderInstallTemplate(): String {
        fun q(path: NioPath) = "'" + path.toString().replace("'", "'\\''") + "'"
        return INSTALL_SHELL_TEMPLATE
            .replace("%CLI%", q(cliBinary))
            .replace("%DIR%", q(linkDir))
            .replace("%LINK%", q(linkPath))
    }

    private fun runSh(command: String): Int = ProcessBuilder("/bin/sh", "-c", command).start().waitFor()

    @Test
    fun `install shell template bails out on a foreign file without touching it`() {
        Files.write(linkPath, byteArrayOf(9))
        assertEquals(40, runSh(renderInstallTemplate()))
        assertEquals(9, Files.readAllBytes(linkPath)[0].toInt())
    }

    @Test
    fun `install shell template creates the link when the path is free`() {
        assertEquals(0, runSh(renderInstallTemplate()))
        assertEquals(cliBinary, Files.readSymbolicLink(linkPath))
    }

    @Test
    fun `install shell template replaces a wrong symlink`() {
        val other = tempDir.resolve("other-cli")
        Files.write(other, byteArrayOf(1))
        Files.createSymbolicLink(linkPath, other)
        assertEquals(0, runSh(renderInstallTemplate()))
        assertEquals(cliBinary, Files.readSymbolicLink(linkPath))
    }

    @Test
    fun `install shell template does not follow a symlink to a directory`() {
        val dir = tempDir.resolve("some-dir")
        Files.createDirectories(dir)
        Files.createSymbolicLink(linkPath, dir)
        assertEquals(0, runSh(renderInstallTemplate()))
        // The link itself was replaced; nothing was created inside the directory
        assertEquals(cliBinary, Files.readSymbolicLink(linkPath))
        assertEquals(0, Files.list(dir).count())
    }

    @Test
    fun `direct install refuses a foreign file at execution time`() {
        // Calls the internal attempt directly, bypassing install()'s
        // pre-check — modeling a file that appeared after that check
        Files.write(linkPath, byteArrayOf(9))
        assertEquals(CliInstallResult.FAILURE, createService().attemptDirectInstall())
        assertEquals(9, Files.readAllBytes(linkPath)[0].toInt())
    }

    @Test
    fun `parse resolved command ignores profile noise`() {
        assertEquals(
            "/usr/local/bin/crosspaste",
            parseResolvedCommand(
                "Welcome to my shell!\nprofile-banner\n$RESOLVED_MARKER/usr/local/bin/crosspaste\n",
            ),
        )
        assertEquals(null, parseResolvedCommand("profile-banner\n/tmp/some/path\n"))
        assertEquals(null, parseResolvedCommand(""))
        assertEquals(null, parseResolvedCommand(RESOLVED_MARKER))
    }

    @Test
    fun `shell probe survives a banner larger than the pipe capacity`() =
        // runBlocking, not runTest: the probe polls with real delays, and
        // runTest's virtual clock would race to the timeout before the real
        // shell produces output (same for the probe tests below)
        runBlocking {
            // 200KB of profile noise before the marker: without draining the
            // stream while the shell runs it would block on a full pipe and
            // the probe would time out
            val probe =
                "i=0; while [ ${'$'}i -lt 5000 ]; do echo banner-line-of-noise-banner-line-of-noise; " +
                    "i=${'$'}((i+1)); done; printf '$RESOLVED_MARKER%s\\n' /tmp/fake-crosspaste"
            val resolved = createService().resolveCommandIn("/bin/sh", probe)
            assertEquals("/tmp/fake-crosspaste", resolved)
        }

    @Test
    fun `shell probe reports null when the command is not found`() =
        runBlocking {
            val probe =
                "p=${'$'}(command -v definitely-not-a-real-command-xyz) && " +
                    "printf '$RESOLVED_MARKER%s\\n' \"${'$'}p\""
            assertEquals(null, createService().resolveCommandIn("/bin/sh", probe))
        }

    @Test
    fun `shell probe returns immediately when a background child keeps stdout open`() =
        runBlocking {
            // The background sleep inherits stdout, so EOF never arrives
            // while it lives; the marker alone must be enough to return
            val probe = "sleep 30 & printf '$RESOLVED_MARKER%s\\n' /tmp/fake-crosspaste"
            val elapsed =
                measureTime {
                    assertEquals(
                        "/tmp/fake-crosspaste",
                        createService().resolveCommandIn("/bin/sh", probe),
                    )
                }
            assertTrue(
                elapsed < 5.seconds,
                "probe must not wait for the background child: took $elapsed",
            )
        }

    @Test
    fun `shell probe times out on a hung foreground shell`() =
        runBlocking {
            val resolved =
                createService().resolveCommandIn(
                    "/bin/sh",
                    "sleep 30",
                    timeout = 1.seconds,
                )
            assertEquals(null, resolved)
        }
}
