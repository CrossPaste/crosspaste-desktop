package com.crosspaste.net

import com.crosspaste.config.ConfigManager
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.engine.*
import kotlinx.coroutines.runBlocking

class PasteServer<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    private val configManager: ConfigManager,
    private val serverFactory: ServerFactory<TEngine, TConfiguration>,
    private val serverModule: ServerModule,
) {

    private val logger = KotlinLogging.logger {}

    private var port = 0

    private var server: ApplicationEngine = createServer(port = configManager.config.port)

    private fun createServer(port: Int): ApplicationEngine {
        return embeddedServer(
            factory = serverFactory.getFactory(),
            port = port,
            configure = serverFactory.getConfigure(),
        ) {
            serverModule.installModules()()
        }
    }

    fun start(): PasteServer<TEngine, TConfiguration> {
        try {
            server.start(wait = false)
        } catch (e: Exception) {
            if (e.message?.contains("already in use") == true) {
                logger.warn { "Port ${configManager.config.port} is already in use" }
                server = createServer(port = 0)
                server.start(wait = false)
            }
        }
        port = runBlocking { server.resolvedConnectors().first().port }
        if (port != configManager.config.port) {
            configManager.updateConfig("port", port)
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
