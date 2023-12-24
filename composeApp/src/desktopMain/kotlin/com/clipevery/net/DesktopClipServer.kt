package com.clipevery.net

import com.clipevery.controller.syncRouting
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking

class DesktopClipServer: ClipServer {

    private val logger = KotlinLogging.logger {}

    private var port = 0

    private var server: NettyApplicationEngine = embeddedServer(Netty, port = 0) {
        install(ContentNegotiation) {
            jackson()
        }
        routing {
            syncRouting()
        }
    }

    override fun start(): ClipServer {
        server.start(wait = false)
        port = runBlocking { server.resolvedConnectors().first().port }
        if (port == 0) {
            logger.error { "Failed to start server" }
        } else {
            logger.info { "Server started at port $port" }
        }
        return this
    }

    override fun stop() {
        server.stop()
    }

    override fun port(): Int {
        return port
    }
}
