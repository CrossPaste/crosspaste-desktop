package com.crosspaste.cli

import com.crosspaste.app.AppInfo
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.config.DesktopAppConfig
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.paste.PasteTagDao
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.paste.PasteDataHelper
import com.crosspaste.paste.PasteReleaseService
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.SearchContentService
import com.crosspaste.paste.item.DefaultPasteItemReader
import com.crosspaste.path.PlatformUserDataPathProvider
import com.crosspaste.path.UserDataPathProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okio.Path.Companion.toOkioPath
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end smoke test for the CLI server on a real Unix domain socket:
 * the same transport the Kotlin/Native CLI uses (spike-verified on macOS,
 * Linux and Windows AF_UNIX; JDK support requires Java 16+, JBR 21 applies).
 */
class CliServerTest {

    private fun createServer(
        userDataDir: Path,
        socketBaseDir: Path,
    ): Pair<CliServer, CliEndpointFile> {
        val configManager = mockk<DesktopConfigManager>()
        every { configManager.getCurrentConfig() } returns DesktopAppConfig(language = "en")
        val pathConfigManager = mockk<CommonConfigManager>()
        every { pathConfigManager.getCurrentConfig() } returns DesktopAppConfig(language = "en")
        val platformProvider =
            object : PlatformUserDataPathProvider {
                override fun getUserDefaultStoragePath() = userDataDir.toFile().toOkioPath()
            }
        val endpointFile = CliEndpointFile(UserDataPathProvider(pathConfigManager, platformProvider))

        val pasteDao = mockk<PasteDao>()
        coEvery { pasteDao.getActiveCount() } returns 3L
        val syncRuntimeInfoDao = mockk<SyncRuntimeInfoDao>()
        coEvery { syncRuntimeInfoDao.getAllSyncRuntimeInfos() } returns listOf()

        val server =
            CliServer(
                appInfo =
                    AppInfo(
                        appInstanceId = "test-instance",
                        appVersion = "2.1.7.9999",
                        appRevision = "Unknown",
                        userName = "tester",
                    ),
                configManager = configManager,
                cliEndpointFile = endpointFile,
                pasteboardService = mockk<PasteboardService>(),
                pasteDao = pasteDao,
                pasteDataHelper = PasteDataHelper(DefaultPasteItemReader()),
                pasteItemReader = DefaultPasteItemReader(),
                pasteReleaseService = mockk<PasteReleaseService>(),
                pasteTagDao = mockk<PasteTagDao>(),
                searchContentService = mockk<SearchContentService>(),
                syncRuntimeInfoDao = syncRuntimeInfoDao,
                socketBaseDir = socketBaseDir,
            )
        return server to endpointFile
    }

    @Test
    fun `start serves status over the unix socket and stop cleans up`() {
        val userDataDir = Files.createTempDirectory("cli-server-data")
        // Keep the base dir short: the resulting socket path must fit sun_path
        val socketBaseDir =
            Path
                .of(System.getProperty("java.io.tmpdir"), "cs-${(100000..999999).random()}")
                .also { Files.createDirectories(it) }
        val (server, endpointFile) = createServer(userDataDir, socketBaseDir)

        runBlocking {
            server.start()
            try {
                val endpointPath = endpointFile.getPath()
                assertTrue(endpointPath.exists(), "endpoint file must be published after start")
                val endpoint = Json.decodeFromString<CliEndpoint>(endpointPath.readText())
                assertEquals(CLI_API_VERSION, endpoint.apiVersion)
                assertEquals("test-instance", endpoint.appInstanceId)
                assertEquals(ProcessHandle.current().pid(), endpoint.pid)

                val socketPath = Path.of(endpoint.socketPath)
                assertTrue(socketPath.exists(), "socket file must exist")
                assertTrue(
                    socketPath.startsWith(socketBaseDir),
                    "socket must live under the configured base dir",
                )

                val response = rawHttpGet(socketPath, "/cli/status")
                assertTrue(
                    response.startsWith("HTTP/1.1 200"),
                    "expected 200 from /cli/status, got: ${response.lineSequence().firstOrNull()}",
                )
                assertTrue(response.contains("\"pasteCount\":3"))

                assertSocketRestrictedToOwner(socketPath)
            } finally {
                server.stop()
            }

            assertFalse(endpointFile.getPath().exists(), "endpoint file must be removed on stop")
        }

        socketBaseDir.toFile().deleteRecursively()
    }

    @Test
    fun `start tightens permissions on a pre-created socket directory it owns`() {
        val posix =
            java.nio.file.FileSystems
                .getDefault()
                .supportedFileAttributeViews()
                .contains("posix")
        if (!posix) {
            return // Windows: directory ACLs apply instead of POSIX bits
        }

        val userDataDir = Files.createTempDirectory("cli-server-data")
        val socketBaseDir =
            Path
                .of(System.getProperty("java.io.tmpdir"), "cs-${(100000..999999).random()}")
                .also { Files.createDirectories(it) }
        // Same directory naming as CliServer.resolveSocketPath
        val sanitizedUserName =
            System.getProperty("user.name").replace(Regex("[^A-Za-z0-9._-]"), "_")
        val socketDir = socketBaseDir.resolve("crosspaste-$sanitizedUserName")
        Files.createDirectories(socketDir)
        Files.setPosixFilePermissions(socketDir, PosixFilePermissions.fromString("rwxrwxrwx"))

        val (server, endpointFile) = createServer(userDataDir, socketBaseDir)
        runBlocking {
            server.start()
            try {
                assertTrue(endpointFile.getPath().exists(), "start must succeed for a self-owned directory")
                assertEquals(
                    PosixFilePermissions.fromString("rwx------"),
                    Files.getPosixFilePermissions(socketDir),
                    "a world-writable self-owned socket directory must be tightened to 0700",
                )
            } finally {
                server.stop()
            }
        }

        socketBaseDir.toFile().deleteRecursively()
    }

    private fun assertSocketRestrictedToOwner(socketPath: Path) {
        val permissions =
            runCatching {
                Files.getPosixFilePermissions(socketPath)
            }.getOrNull() ?: return // Windows: ACL of the per-user temp dir applies instead
        assertEquals(
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            permissions,
        )
    }

    private fun rawHttpGet(
        socketPath: Path,
        requestPath: String,
    ): String =
        SocketChannel.open(StandardProtocolFamily.UNIX).use { channel ->
            channel.connect(UnixDomainSocketAddress.of(socketPath))
            val request =
                "GET $requestPath HTTP/1.1\r\n" +
                    "Host: localhost\r\n" +
                    "Connection: close\r\n" +
                    "\r\n"
            channel.write(ByteBuffer.wrap(request.toByteArray()))

            val builder = StringBuilder()
            val buffer = ByteBuffer.allocate(8192)
            while (channel.read(buffer) >= 0) {
                buffer.flip()
                builder.append(Charsets.UTF_8.decode(buffer))
                buffer.clear()
            }
            builder.toString()
        }
}
