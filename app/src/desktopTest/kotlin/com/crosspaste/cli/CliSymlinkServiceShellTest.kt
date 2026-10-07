package com.crosspaste.cli

import com.crosspaste.app.AppFileType
import com.crosspaste.path.AppPathProvider
import com.crosspaste.platform.Platform
import com.crosspaste.test.IntegrationTest
import kotlinx.coroutines.runBlocking
import okio.Path
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime
import java.nio.file.Path as NioPath

/**
 * The parts of [CliSymlinkService] whose value is running a real /bin/sh: the install
 * shell template and the login-shell command probe (including its wall-clock timeouts).
 * Pure path/state logic lives in [CliSymlinkServiceTest].
 */
@IntegrationTest
class CliSymlinkServiceShellTest {

    private lateinit var tempDir: NioPath
    private lateinit var cliBinary: NioPath
    private lateinit var linkDir: NioPath
    private lateinit var linkPath: NioPath

    @BeforeTest
    fun setUp() {
        tempDir = createTempDirectory("cli-symlink-shell-test")
        val payloadBinDir = tempDir.resolve("Resources").resolve("bin")
        Files.createDirectories(payloadBinDir)
        cliBinary = payloadBinDir.resolve("crosspaste-cli")
        Files.write(cliBinary, byteArrayOf(1))
        linkDir = tempDir.resolve("usr-local-bin")
        Files.createDirectories(linkDir)
        linkPath = linkDir.resolve("crosspaste")
    }

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    private fun createService(): CliSymlinkService {
        val root = tempDir.toOkioPath()
        val appPathProvider =
            object : AppPathProvider {
                override val userHome: Path = root
                override val pasteAppPath: Path = root
                override val pasteAppJarPath: Path = tempDir.resolve("Resources").toOkioPath()
                override val pasteAppExePath: Path = root
                override val pasteUserPath: Path = root

                override fun resolve(
                    fileName: String?,
                    appFileType: AppFileType,
                ): Path = root
            }
        return CliSymlinkService(
            appPathProvider = appPathProvider,
            platform = Platform(name = Platform.MACOS, arch = "arm64", bitMode = 64, version = "14.0"),
            linkPath = linkPath.toOkioPath(),
        )
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
