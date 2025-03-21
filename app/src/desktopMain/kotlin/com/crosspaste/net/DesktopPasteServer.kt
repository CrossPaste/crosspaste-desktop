package com.crosspaste.net

import com.crosspaste.config.ReadWriteConfig
import com.crosspaste.net.exception.ExceptionHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking

class DesktopPasteServer(
    private val readWritePort: ReadWriteConfig<Int>,
    private val exceptionHandler: ExceptionHandler,
    serverFactory: ServerFactory<NettyApplicationEngine, NettyApplicationEngine.Configuration>,
    serverModule: ServerModule,
) :
    PasteServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>(
            serverFactory,
            serverModule,
        ) {

    private val logger = KotlinLogging.logger {}

    override fun start() {
        runCatching {
            server = createServer(port = readWritePort.getValue())
            server?.start(wait = false)
        }.onFailure { e ->
            if (exceptionHandler.isPortAlreadyInUse(e)) {
                logger.warn { "Port ${readWritePort.getValue()} is already in use" }
                server = createServer(port = 0)
                server?.start(wait = false)
            }
        }
        runBlocking {
            server?.application?.engine?.resolvedConnectors()?.first()?.port?.let {
                port = it
            }
        }
        if (port != readWritePort.getValue()) {
            readWritePort.setValue(port)
        }
        logger.info { "Server started at port $port" }
    }

    override fun stop() {
        server?.stop()
        server = null
    }
}
