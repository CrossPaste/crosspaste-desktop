package com.crosspaste.net

import io.ktor.server.engine.*

interface ServerFactory<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration> {

    fun getFactory(): ApplicationEngineFactory<TEngine, TConfiguration>

    fun getConfigure(): TConfiguration.() -> Unit
}
