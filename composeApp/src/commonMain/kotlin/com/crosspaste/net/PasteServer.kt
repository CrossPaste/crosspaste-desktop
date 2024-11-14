package com.crosspaste.net

import com.crosspaste.config.ReadWriteConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.engine.*
import kotlinx.coroutines.runBlocking

class PasteServer<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    private val readWritePort: ReadWriteConfig<Int>,
    private val serverFactory: ServerFactory<TEngine, TConfiguration>,
    private val serverModule: ServerModule,
) {

    private val logger = KotlinLogging.logger {}

    private var port = 0

    private var server: EmbeddedServer<TEngine, TConfiguration> = createServer(port = readWritePort.getValue())

    private fun createServer(port: Int): EmbeddedServer<TEngine, TConfiguration> {
        return embeddedServer(
            factory = serverFactory.getFactory(),
            configure = {
                connector { this.port = port }
                serverFactory.getConfigure()()
            },
        ) {
            serverModule.installModules()()
        }
    }

    fun start(): PasteServer<TEngine, TConfiguration> {
        try {
            server.start(wait = false)
        } catch (e: Exception) {
            if (e.message?.contains("already in use") == true) {
                logger.warn { "Port ${readWritePort.getValue()} is already in use" }
                server = createServer(port = 0)
                server.start(wait = false)
            }
        }
        port = runBlocking { server.application.engine.resolvedConnectors().first().port }
        if (port != readWritePort.getValue()) {
            readWritePort.setValue(port)
        }
        logger.info { "Server started at port $port" }
        return this
    }

    fun stop() {
        server.stop()
    }

    fun port(): Int {
        return port
    }
}
