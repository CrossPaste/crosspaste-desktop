package com.crosspaste.mcp

import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal interface McpServerInstance {

    suspend fun start()

    suspend fun stop()
}

internal fun interface McpServerInstanceFactory {

    fun create(port: Int): McpServerInstance
}

private class DefaultMcpServerInstanceFactory(
    private val mcpToolProvider: McpToolProvider,
    private val mcpResourceProvider: McpResourceProvider,
) : McpServerInstanceFactory {

    override fun create(port: Int): McpServerInstance {
        val mcpServer =
            Server(
                serverInfo =
                    Implementation(
                        name = "crosspaste-mcp-server",
                        version = "1.0.0",
                    ),
                options =
                    ServerOptions(
                        capabilities =
                            ServerCapabilities(
                                tools = ServerCapabilities.Tools(listChanged = true),
                                resources =
                                    ServerCapabilities.Resources(
                                        subscribe = false,
                                        listChanged = true,
                                    ),
                            ),
                    ),
            )

        mcpToolProvider.registerTools(mcpServer)
        mcpResourceProvider.registerResources(mcpServer)

        val server =
            embeddedServer(CIO, host = "127.0.0.1", port = port) {
                mcp {
                    mcpServer
                }
            }
        return KtorMcpServerInstance(server)
    }
}

private class KtorMcpServerInstance(
    private val server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>,
) : McpServerInstance {

    override suspend fun start() {
        server.startSuspend(wait = false)
    }

    override suspend fun stop() {
        server.stopSuspend()
    }
}

class DesktopMcpServer internal constructor(
    private var configuredPort: Int,
    private val serverFactory: McpServerInstanceFactory,
    private val lifecycleDispatcher: CoroutineDispatcher,
) : McpServer {

    constructor(
        mcpPort: Int,
        mcpToolProvider: McpToolProvider,
        mcpResourceProvider: McpResourceProvider,
    ) : this(
        configuredPort = mcpPort,
        serverFactory = DefaultMcpServerInstanceFactory(mcpToolProvider, mcpResourceProvider),
        lifecycleDispatcher = ioDispatcher,
    )

    private val logger = KotlinLogging.logger {}
    private val lifecycleMutex = Mutex()

    private var server: McpServerInstance? = null

    @Volatile
    private var actualPort: Int = 0

    override suspend fun start() {
        runLifecycleOperation {
            lifecycleMutex.withLock {
                if (server != null) {
                    return@withLock
                }

                val port = resolvePort(configuredPort)
                server = createStartedServer(port)
                actualPort = port
                logger.info { "MCP Server started at port $actualPort (WebSocket)" }
            }
        }
    }

    override suspend fun stop() {
        runLifecycleOperation {
            lifecycleMutex.withLock {
                stopCurrentServer()
            }
        }
    }

    override suspend fun restart(newPort: Int) {
        runLifecycleOperation {
            lifecycleMutex.withLock {
                val port = resolvePort(newPort)
                val currentServer = server
                if (currentServer != null && actualPort == port) {
                    configuredPort = newPort
                    return@withLock
                }

                // Keep the working endpoint available until its replacement has bound.
                val replacement = createStartedServer(port)
                try {
                    currentServer?.stop()
                } catch (e: Throwable) {
                    withContext(NonCancellable) {
                        cleanupServer(replacement, "replacement MCP server")
                    }
                    throw e
                }

                server = replacement
                configuredPort = newPort
                actualPort = port
                logger.info { "MCP Server restarted at port $actualPort (WebSocket)" }
            }
        }
    }

    override fun port(): Int = actualPort

    private suspend fun createStartedServer(port: Int): McpServerInstance {
        val candidate = serverFactory.create(port)
        try {
            candidate.start()
            return candidate
        } catch (e: Throwable) {
            withContext(NonCancellable) {
                cleanupServer(candidate, "partially started MCP server")
            }
            throw e
        }
    }

    private suspend fun stopCurrentServer() {
        val currentServer = server ?: return
        currentServer.stop()
        server = null
        actualPort = 0
        logger.info { "MCP Server stopped" }
    }

    private suspend fun cleanupServer(
        target: McpServerInstance,
        description: String,
    ) {
        runCatching { target.stop() }
            .onFailure { e -> logger.warn(e) { "Failed to clean up $description" } }
    }

    private suspend fun runLifecycleOperation(operation: suspend () -> Unit) {
        withContext(lifecycleDispatcher) {
            operation()
        }
    }

    private fun resolvePort(port: Int): Int {
        val resolvedPort = if (port > 0) port else DEFAULT_PORT
        require(resolvedPort in 1..65535) { "Invalid MCP server port: $port" }
        return resolvedPort
    }

    companion object {
        const val DEFAULT_PORT = 13130
    }
}
