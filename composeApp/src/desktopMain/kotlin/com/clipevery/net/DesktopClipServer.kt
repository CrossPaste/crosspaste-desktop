package com.clipevery.net

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking


class DesktopClipServer: ClipServer {

    private val logger = KotlinLogging.logger {}

    private var port = 0


    private var server: NettyApplicationEngine = embeddedServer(Netty, port = 0) {
        routing {
            get("/") {
                call.respondText("Hello, world!")
            }
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