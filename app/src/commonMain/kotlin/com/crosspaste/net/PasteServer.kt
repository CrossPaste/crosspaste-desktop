package com.crosspaste.net

import io.ktor.server.application.serverConfig
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.util.logging.KtorSimpleLogger
import kotlin.coroutines.CoroutineContext

abstract class PasteServer<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    private val serverFactory: ServerFactory<TEngine, TConfiguration>,
    private val serverModule: ServerModule,
) : Server {

    protected var port = 0

    protected var server: EmbeddedServer<TEngine, TConfiguration>? = null

    protected abstract val parentCoroutineContext: CoroutineContext

    protected fun createServer(port: Int): EmbeddedServer<TEngine, TConfiguration> {
        val environment =
            applicationEnvironment {
                this.log = KtorSimpleLogger("io.ktor.server.Application")
            }
        val applicationProperties =
            serverConfig(environment) {
                this.parentCoroutineContext = this@PasteServer.parentCoroutineContext
                this.watchPaths = listOf()
                this.module { serverModule.installModules()() }
            }

        return embeddedServer(
            factory = serverFactory.getFactory(),
            rootConfig = applicationProperties,
            configure = {
                connector { this.port = port }
                serverFactory.getConfigure()()
            },
        )
    }

    override fun port(): Int = port
}
