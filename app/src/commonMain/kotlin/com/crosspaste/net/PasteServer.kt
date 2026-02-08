package com.crosspaste.net

import io.ktor.server.application.serverConfig
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.CoroutineContext

abstract class PasteServer<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    private val serverFactory: ServerFactory<TEngine, TConfiguration>,
    private val serverModule: ServerModule,
) : Server {

    private val _portFlow = MutableStateFlow(0)
    override val portFlow: StateFlow<Int> = _portFlow

    protected var port: Int
        get() = _portFlow.value
        set(value) {
            _portFlow.value = value
        }

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
