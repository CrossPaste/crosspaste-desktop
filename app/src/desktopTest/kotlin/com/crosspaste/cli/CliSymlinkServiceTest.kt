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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
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

    private fun macosPlatform() = Platform(name = Platform.MACOS, arch = "arm64", bitMode = 64, version = "14.0")

    private fun createService(
        platform: Platform = macosPlatform(),
        jarDir: NioPath = tempDir.resolve("Resources"),
        escalatedInstall: ((Path, Path) -> CliInstallResult)? = null,
    ): CliSymlinkService =
        CliSymlinkService(
            appPathProvider = fakeAppPathProvider(jarDir),
            platform = platform,
            linkPath = linkPath.toOkioPath(),
            escalatedInstall = escalatedInstall,
        )

    private suspend fun refreshedState(service: CliSymlinkService): CliSymlinkState {
        service.refresh()
        return service.state.value
    }

    @Test
    fun `state starts at probing before the first refresh`() {
        assertEquals(CliSymlinkState.PROBING, createService().state.value)
    }

    @Test
    fun `binary missing when cli binary is absent`() =
        runTest {
            Files.delete(cliBinary)
            assertEquals(CliSymlinkState.BINARY_MISSING, refreshedState(createService()))
        }

    @Test
    fun `externally managed on windows when the packaged exe exists`() =
        runTest {
            Files.write(payloadBinDir.resolve("crosspaste-cli.exe"), byteArrayOf(1))
            val platform = Platform(name = Platform.WINDOWS, arch = "amd64", bitMode = 64, version = "10.0")
            assertEquals(CliSymlinkState.EXTERNALLY_MANAGED, refreshedState(createService(platform)))
        }

    @Test
    fun `externally managed on linux when the packaged cli exists`() =
        runTest {
            val platform = Platform(name = Platform.LINUX, arch = "amd64", bitMode = 64, version = "6.1")
            assertEquals(CliSymlinkState.EXTERNALLY_MANAGED, refreshedState(createService(platform)))
        }

    @Test
    fun `binary missing on windows when only the unix binary name exists`() =
        runTest {
            // setUp created plain crosspaste-cli; windows resolves crosspaste-cli.exe
            val platform = Platform(name = Platform.WINDOWS, arch = "amd64", bitMode = 64, version = "10.0")
            assertEquals(CliSymlinkState.BINARY_MISSING, refreshedState(createService(platform)))
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
    fun `install fails when externally managed`() =
        runTest {
            val platform = Platform(name = Platform.LINUX, arch = "amd64", bitMode = 64, version = "6.1")
            val service = createService(platform)
            assertEquals(CliInstallResult.FAILURE, service.install())
            assertEquals(CliSymlinkState.EXTERNALLY_MANAGED, service.state.value)
        }

    @Test
    fun `install fails when the binary is missing`() =
        runTest {
            Files.delete(cliBinary)
            val service = createService()
            assertEquals(CliInstallResult.FAILURE, service.install())
            assertEquals(CliSymlinkState.BINARY_MISSING, service.state.value)
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
        assertEquals(
            CliInstallResult.FAILURE,
            createService().attemptDirectInstall(cliBinary.toOkioPath()),
        )
        assertEquals(9, Files.readAllBytes(linkPath)[0].toInt())
    }

    @Test
    fun `dev resolution prefers a valid configured path`() {
        val configured = tempDir.resolve("custom-cli")
        Files.write(configured, byteArrayOf(1))
        assertEquals(
            configured.toOkioPath(),
            resolveDevCliBinary(configured.toString(), tempDir, "macosArm64", "crosspaste-cli"),
        )
    }

    @Test
    fun `dev resolution fails fast on a configured but missing path`() {
        assertFailsWith<IllegalStateException> {
            resolveDevCliBinary(
                tempDir.resolve("does-not-exist").toString(),
                tempDir,
                "macosArm64",
                "crosspaste-cli",
            )
        }
    }

    @Test
    fun `dev resolution walks up to the conventional dist output`() {
        val distBinary =
            tempDir
                .resolve("cli")
                .resolve("dist")
                .resolve("macosArm64")
                .resolve("crosspaste-cli")
        Files.createDirectories(distBinary.parent)
        Files.write(distBinary, byteArrayOf(1))
        // Start two levels below the repo root, as an app-module cwd would be
        val startDir = tempDir.resolve("app").resolve("build")
        Files.createDirectories(startDir)
        assertEquals(
            distBinary.toOkioPath(),
            resolveDevCliBinary(null, startDir, "macosArm64", "crosspaste-cli"),
        )
    }

    @Test
    fun `dev resolution returns null when nothing is built or configured`() {
        assertEquals(null, resolveDevCliBinary(null, tempDir, "macosArm64", "crosspaste-cli"))
    }

    @Test
    fun `dev resolution absolutizes a relative configured path`() {
        // A relative path written verbatim into /usr/local/bin/crosspaste
        // would resolve relative to /usr/local/bin as a dangling link — yet
        // still compare equal as INSTALLED
        val configured = tempDir.resolve("custom-cli")
        Files.write(configured, byteArrayOf(1))
        val relative = NioPath.of("").toAbsolutePath().relativize(configured)
        assertTrue(!relative.isAbsolute)
        val resolved = resolveDevCliBinary(relative.toString(), tempDir, "macosArm64", "crosspaste-cli")
        assertTrue(resolved!!.isAbsolute)
        assertEquals(configured.toAbsolutePath().normalize(), resolved.toNioPath())
    }

    @Test
    fun `refresh picks up a binary built after startup`() =
        runTest {
            // Models the dev convention resolver: nothing at first, then the
            // user runs :cli:assembleDist while the app is up
            val lateBinary = tempDir.resolve("late-cli")
            val service =
                CliSymlinkService(
                    appPathProvider = fakeAppPathProvider(tempDir.resolve("Resources")),
                    platform = macosPlatform(),
                    linkPath = linkPath.toOkioPath(),
                    binaryResolverOverride = {
                        lateBinary.takeIf { Files.isRegularFile(it) }?.toOkioPath()
                    },
                )
            assertEquals(CliSymlinkState.BINARY_MISSING, refreshedState(service))
            assertEquals(null, service.cliBinaryPath.value)
            Files.write(lateBinary, byteArrayOf(1))
            assertEquals(CliSymlinkState.NOT_INSTALLED, refreshedState(service))
            assertEquals(lateBinary.toOkioPath(), service.cliBinaryPath.value)
        }

    @Test
    fun `dist target and binary name follow the host platform`() {
        fun platform(
            name: String,
            arch: String,
        ) = Platform(name = name, arch = arch, bitMode = 64, version = "1")
        assertEquals("macosArm64", devCliDistTarget(platform(Platform.MACOS, "aarch64")))
        assertEquals("macosX64", devCliDistTarget(platform(Platform.MACOS, "x86_64")))
        assertEquals("linuxArm64", devCliDistTarget(platform(Platform.LINUX, "aarch64")))
        assertEquals("linuxX64", devCliDistTarget(platform(Platform.LINUX, "amd64")))
        assertEquals("mingwX64", devCliDistTarget(platform(Platform.WINDOWS, "amd64")))
        assertEquals("crosspaste-cli.exe", cliBinaryFileName(platform(Platform.WINDOWS, "amd64")))
        assertEquals("crosspaste-cli", cliBinaryFileName(platform(Platform.MACOS, "aarch64")))
    }

    @Test
    fun `parse where output takes the first non-empty line`() {
        assertEquals(
            "C:\\Users\\me\\AppData\\Local\\Microsoft\\WindowsApps\\crosspaste-cli.exe",
            parseWherePath("C:\\Users\\me\\AppData\\Local\\Microsoft\\WindowsApps\\crosspaste-cli.exe\r"),
        )
        assertEquals(null, parseWherePath(""))
        assertEquals(null, parseWherePath("  \r"))
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
    fun `shell probe marker starts on a new line after unterminated profile output`() =
        runBlocking {
            val probe =
                "printf profile-without-newline; " +
                    "printf '\\n$RESOLVED_MARKER%s\\n' /tmp/fake-crosspaste"
            assertEquals(
                "/tmp/fake-crosspaste",
                createService().resolveCommandIn("/bin/sh", probe),
            )
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
            val probe = "sleep 2 & printf '$RESOLVED_MARKER%s\\n' /tmp/fake-crosspaste"
            val elapsed =
                measureTime {
                    assertEquals(
                        "/tmp/fake-crosspaste",
                        createService().resolveCommandIn("/bin/sh", probe),
                    )
                }
            assertTrue(
                elapsed < 1.seconds,
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

    @Test
    fun `shell probe timeout remains effective under continuous output`() =
        runBlocking {
            val elapsed =
                measureTime {
                    assertEquals(
                        null,
                        createService().resolveCommandIn(
                            "/bin/sh",
                            "while :; do printf noise-line\\n; done",
                            timeout = 300.milliseconds,
                        ),
                    )
                }
            assertTrue(elapsed < 2.seconds, "continuous output bypassed timeout: took $elapsed")
        }
}
