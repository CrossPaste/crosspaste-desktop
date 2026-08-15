package com.crosspaste.cli

import com.crosspaste.app.AppFileType
import com.crosspaste.path.AppPathProvider
import com.crosspaste.platform.Platform
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
}
