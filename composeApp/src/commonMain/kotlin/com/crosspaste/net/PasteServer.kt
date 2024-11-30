package com.crosspaste.net

import com.crosspaste.config.ReadWriteConfig
import com.crosspaste.net.exception.ExceptionHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.engine.*
import kotlinx.coroutines.runBlocking

class PasteServer<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    private val readWritePort: ReadWriteConfig<Int>,
    private val exceptionHandler: ExceptionHandler,
    private val serverFactory: ServerFactory<TEngine, TConfiguration>,
    private val serverModule: ServerModule,
) : Server {

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

    override fun start() {
        try {
            server.start(wait = false)
        } catch (e: Exception) {
            if (exceptionHandler.isPortAlreadyInUse(e)) {
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
    }

    override fun stop() {
        server.stop()
    }

    override fun port(): Int {
        return port
    }
}
