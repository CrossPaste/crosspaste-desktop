package com.clipevery.net

import com.clipevery.model.sync.RequestSyncInfo
import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.coroutines.runBlocking

class DesktopClipServer: ClipServer {

    private val logger = KotlinLogging.logger {}

    private var port = 0

    private var server: NettyApplicationEngine = embeddedServer(Netty, port = 0) {
        install(OpenAPIGen) {
            // this servers OpenAPI definition on /openapi.json
            serveOpenApiJson = true
            // this servers Swagger UI on /swagger-ui/index.html
            serveSwaggerUi = true
            info {
                title = "Clipevery API"
            }
        }
        install(ContentNegotiation) {
            jackson()
        }
        apiRouting {
            route("/sync") {
                post<String, RequestSyncInfo, RequestSyncInfo> { _, requestSyncInfo ->
                    respond(requestSyncInfo)
                }
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