package com.clipevery.net

import com.clipevery.config.ConfigManager
import com.clipevery.exception.StandardErrorCode
import com.clipevery.net.exception.signalExceptionHandler
import com.clipevery.net.plugin.SIGNAL_SERVER_DECRYPT_PLUGIN
import com.clipevery.net.plugin.SIGNAL_SERVER_ENCRYPT_PLUGIN
import com.clipevery.routing.clipRouting
import com.clipevery.routing.pullRouting
import com.clipevery.routing.syncRouting
import com.clipevery.utils.DesktopJsonUtils
import com.clipevery.utils.failResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.netty.channel.ChannelOption
import kotlinx.coroutines.runBlocking
import java.net.BindException

class DesktopClipServer(private val configManager: ConfigManager) : ClipServer {

    private val logger = KotlinLogging.logger {}

    private var port = 0

    private var server: NettyApplicationEngine = createServer(port = configManager.config.port)

    private fun createServer(port: Int): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port,
            configure = {
                configureBootstrap = {
                    childOption(ChannelOption.TCP_NODELAY, true)
                    childOption(ChannelOption.SO_KEEPALIVE, true)
                }
            },
        ) {
            install(Compression) {
                gzip {
                    priority = 1.0
                }
                deflate {
                    priority = 10.0
                    minimumSize(1024)
                }
            }
            install(ContentNegotiation) {
                json(DesktopJsonUtils.JSON)
            }
            install(StatusPages) {
                exception(Exception::class) { call, cause ->
                    logger.error(cause) { "Unhandled exception" }
                    failResponse(call, StandardErrorCode.UNKNOWN_ERROR.toErrorCode())
                }
                signalExceptionHandler()
            }
            install(SIGNAL_SERVER_DECRYPT_PLUGIN) {}
            install(SIGNAL_SERVER_ENCRYPT_PLUGIN) {}
            intercept(ApplicationCallPipeline.Setup) {
                logger.info { "Received request: ${call.request.httpMethod.value} ${call.request.uri} ${call.request.contentType()}" }
            }
            routing {
                syncRouting()
                clipRouting()
                pullRouting()
            }
        }
    }

    override fun start(): ClipServer {
        try {
            server.start(wait = false)
        } catch (e: BindException) {
            logger.warn { "Port ${configManager.config.port} is already in use" }
            server = createServer(port = 0)
            server.start(wait = false)
        }
        port = runBlocking { server.resolvedConnectors().first().port }
        if (port != configManager.config.port) {
            configManager.updateConfig {
                it.copy(port = port)
            }
        }
        logger.info { "Server started at port $port" }
        return this
    }

    override fun stop() {
        server.stop()
    }

    override fun port(): Int {
        return port
    }
}
