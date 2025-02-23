package com.crosspaste.net

import io.ktor.server.engine.*

abstract class PasteServer<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    private val serverFactory: ServerFactory<TEngine, TConfiguration>,
    private val serverModule: ServerModule,
) : Server {

    protected var port = 0

    protected var server: EmbeddedServer<TEngine, TConfiguration>? = null

    protected fun createServer(port: Int): EmbeddedServer<TEngine, TConfiguration> {
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

    override fun port(): Int {
        return port
    }
}
