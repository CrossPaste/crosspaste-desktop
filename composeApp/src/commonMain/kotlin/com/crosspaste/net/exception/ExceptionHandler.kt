package com.crosspaste.net.exception

import io.ktor.server.plugins.statuspages.*

interface ExceptionHandler {
    fun handler(): StatusPagesConfig.() -> Unit
}
