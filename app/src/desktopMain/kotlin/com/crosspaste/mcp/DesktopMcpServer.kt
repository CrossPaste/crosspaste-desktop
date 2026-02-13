package com.crosspaste.mcp

import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.withContext

class DesktopMcpServer(
    private var mcpPort: Int,
    private val mcpToolProvider: McpToolProvider,
    private val mcpResourceProvider: McpResourceProvider,
) : McpServer {

    private val logger = KotlinLogging.logger {}

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var actualPort: Int = 0

    override suspend fun start() {
        withContext(ioDispatcher) {
            runCatching {
                val port = if (mcpPort > 0) mcpPort else DEFAULT_PORT
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

                server =
                    embeddedServer(Netty, host = "127.0.0.1", port = port) {
                        mcp {
                            mcpServer
                        }
                    }.start(wait = false)

                actualPort = port
                logger.info { "MCP Server started at port $actualPort (WebSocket)" }
            }.onFailure { e ->
                logger.error(e) { "Failed to start MCP Server" }
            }
        }
    }

    override suspend fun stop() {
        runCatching {
            server?.stop()
            server = null
            logger.info { "MCP Server stopped" }
        }.onFailure { e ->
            logger.error(e) { "Failed to stop MCP Server" }
        }
    }

    override suspend fun restart(newPort: Int) {
        stop()
        mcpPort = newPort
        start()
    }

    override fun port(): Int = actualPort

    companion object {
        const val DEFAULT_PORT = 13130
    }
}
