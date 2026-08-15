package com.crosspaste.net

import com.crosspaste.config.ReadWriteConfig
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class DesktopPasteServer(
    private val readWritePort: ReadWriteConfig<Int>,
    private val exceptionHandler: ExceptionHandler,
    serverFactory: ServerFactory<NettyApplicationEngine, NettyApplicationEngine.Configuration>,
    serverModule: ServerModule,
) : PasteServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>(
        serverFactory,
        serverModule,
    ) {

    private val logger = KotlinLogging.logger {}

    override suspend fun start() =
        withContext(ioDispatcher) {
            val configuredPort = readWritePort.getValue()
            try {
                try {
                    startServer(configuredPort)
                } catch (e: Throwable) {
                    cleanupFailedServer()
                    if (!exceptionHandler.isPortAlreadyInUse(e)) {
                        logger.error(e) { "Failed to start server" }
                        throw e
                    }

                    logger.warn { "Port $configuredPort is already in use, retrying on random port" }
                    startServer(0)
                }

                port =
                    checkNotNull(server)
                        .application.engine
                        .resolvedConnectors()
                        .firstOrNull()
                        ?.port
                        ?: error("Started server has no resolved connector")
                if (port != configuredPort) {
                    readWritePort.setValue(port)
                }
                logger.info { "Server started at port $port" }
            } catch (e: Throwable) {
                cleanupFailedServer()
                logger.error(e) { "Server startup failed" }
                throw e
            }
        }

    private fun startServer(port: Int) {
        server = createServer(port = port)
        checkNotNull(server).start(wait = false)
    }

    private suspend fun cleanupFailedServer() {
        runCatching { server?.stop() }
            .onFailure { e -> logger.warn(e) { "Failed to clean up partially started server" } }
        server = null
    }

    override suspend fun stop() {
        server?.stop()
        server = null
    }

    private val coroutineExceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            if (exceptionHandler.isPortAlreadyInUse(throwable)) {
                logger.warn { "Port already in use exception caught in coroutine: ${throwable.message}" }
            } else {
                logger.error(throwable) { "Uncaught exception in server coroutine: ${throwable.message}" }
            }
        }

    override val parentCoroutineContext: CoroutineContext
        get() = ioDispatcher + coroutineExceptionHandler
}
