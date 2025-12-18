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
            server?.application?.engine?.resolvedConnectors()?.first()?.port?.let {
                port = it
            }
            if (port != readWritePort.getValue()) {
                readWritePort.setValue(port)
            }
            logger.info { "Server started at port $port" }
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
