package com.crosspaste.cli

import com.crosspaste.app.AppEnv
import com.crosspaste.app.AppInfo
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.paste.PasteTagDao
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.paste.PasteDataHelper
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.SearchContentService
import com.crosspaste.paste.item.PasteItemReader
import com.crosspaste.utils.getAppEnvUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import kotlin.time.Duration.Companion.milliseconds

/**
 * Local-only HTTP API for the CLI, served over a Unix domain socket.
 *
 * Access control model (design D2): the socket has no TCP port, so it is
 * unreachable from the network; the socket file (0600) and its parent
 * directory (0700, ownership-verified) keep other local users out. Same-user
 * processes are inside the trust boundary — they can already read the
 * database files directly.
 *
 * Runs as an independent CIO server so the Netty sync server stays untouched.
 * On successful start it publishes [CliEndpoint] via [CliEndpointFile]; the
 * CLI discovers the socket exclusively through that file.
 */
class CliServer(
    private val appInfo: AppInfo,
    private val configManager: DesktopConfigManager,
    private val cliEndpointFile: CliEndpointFile,
    private val pasteboardService: PasteboardService,
    private val pasteDao: PasteDao,
    private val pasteDataHelper: PasteDataHelper,
    private val pasteItemReader: PasteItemReader,
    private val pasteTagDao: PasteTagDao,
    private val searchContentService: SearchContentService,
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
    private val socketBaseDir: Path = Paths.get(System.getProperty("java.io.tmpdir")),
) {

    companion object {
        // macOS limits sun_path to 104 bytes; leave headroom
        private const val MAX_SOCKET_PATH_BYTES = 100

        private val SOCKET_READY_POLL_INTERVAL = 50.milliseconds
        private const val SOCKET_READY_MAX_POLLS = 100

        private val socketFileName: String
            get() {
                val appEnv = getAppEnvUtils().getCurrentAppEnv()
                return if (appEnv == AppEnv.PRODUCTION) {
                    "cli.sock"
                } else {
                    // Keep dev/test instances from hijacking the production socket
                    "cli-${appEnv.name.lowercase()}.sock"
                }
            }
    }

    private val logger = KotlinLogging.logger {}

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    private var socketPath: Path? = null

    suspend fun start() {
        withContext(ioDispatcher) {
            runCatching {
                val socket = resolveSocketPath()
                Files.deleteIfExists(socket)
                server =
                    embeddedServer(
                        CIO,
                        configure = {
                            unixConnector(socket.toString())
                        },
                    ) {
                        cliServerModule()
                    }.start(wait = false)
                socketPath = socket
                awaitSocketFile(socket)
                restrictSocketToOwner(socket)
                cliEndpointFile.write(
                    CliEndpoint(
                        pid = ProcessHandle.current().pid(),
                        socketPath = socket.toString(),
                        apiVersion = CLI_API_VERSION,
                        appInstanceId = appInfo.appInstanceId,
                    ),
                )
                logger.info { "CLI server started on unix socket $socket" }
            }.onFailure { e ->
                logger.error(e) { "Failed to start CLI server" }
                runCatching { server?.stop() }
                server = null
                socketPath = null
            }
        }
    }

    suspend fun stop() {
        withContext(ioDispatcher) {
            runCatching {
                cliEndpointFile.delete()
                server?.stop()
                server = null
                socketPath?.let { Files.deleteIfExists(it) }
                socketPath = null
                logger.info { "CLI server stopped" }
            }.onFailure { e ->
                logger.error(e) { "Failed to stop CLI server" }
            }
        }
    }

    private fun Application.cliServerModule() {
        install(ContentNegotiation) {
            json(Json { encodeDefaults = true })
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                call.respond(
                    HttpStatusCode.InternalServerError,
                    CliMessageDto(cause.message ?: "Internal error"),
                )
            }
        }
        routing {
            cliRouting(
                appInfo = appInfo,
                configManager = configManager,
                pasteboardService = pasteboardService,
                pasteDao = pasteDao,
                pasteDataHelper = pasteDataHelper,
                pasteItemReader = pasteItemReader,
                pasteTagDao = pasteTagDao,
                searchContentService = searchContentService,
                syncRuntimeInfoDao = syncRuntimeInfoDao,
            )
        }
    }

    /**
     * The socket lives in a short per-user temp directory instead of the user
     * data path: sun_path is limited to ~104 bytes on macOS and a custom
     * storagePath could exceed it. The directory must be owned by the current
     * user with 0700 so that no other local user can pre-create it and plant a
     * fake socket (relevant on Linux where /tmp is shared).
     */
    private fun resolveSocketPath(): Path {
        val userName = System.getProperty("user.name")
        val sanitizedUserName = userName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val socketDir = socketBaseDir.resolve("crosspaste-$sanitizedUserName")

        Files.createDirectories(socketDir)
        val posix =
            runCatching {
                Files.setPosixFilePermissions(
                    socketDir,
                    setOf(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE,
                    ),
                )
            }.isSuccess
        if (posix) {
            val owner = Files.getOwner(socketDir).name
            check(owner == userName) {
                "CLI socket directory $socketDir is owned by '$owner', not '$userName'; refusing to start"
            }
        }

        val socket = socketDir.resolve(socketFileName)
        check(socket.toString().toByteArray(Charsets.UTF_8).size <= MAX_SOCKET_PATH_BYTES) {
            "CLI socket path too long for sun_path: $socket"
        }
        return socket
    }

    private suspend fun awaitSocketFile(socket: Path) {
        repeat(SOCKET_READY_MAX_POLLS) {
            if (Files.exists(socket)) {
                return
            }
            delay(SOCKET_READY_POLL_INTERVAL)
        }
        error("CLI socket file $socket did not appear after engine start")
    }

    private fun restrictSocketToOwner(socket: Path) {
        runCatching {
            Files.setPosixFilePermissions(
                socket,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }.onFailure {
            // Windows: no POSIX permissions on AF_UNIX socket files; the
            // per-user temp directory ACL restricts access instead
        }
    }
}
