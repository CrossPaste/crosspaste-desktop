package com.crosspaste.net

import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory

class DesktopServerFactory : ServerFactory<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
    override fun getFactory(): ApplicationEngineFactory<CIOApplicationEngine, CIOApplicationEngine.Configuration> = CIO

    override fun getConfigure(): CIOApplicationEngine.Configuration.() -> Unit = {}
}
