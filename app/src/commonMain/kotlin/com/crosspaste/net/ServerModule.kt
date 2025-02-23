package com.crosspaste.net

import io.ktor.server.application.*

interface ServerModule {

    fun installModules(): Application.() -> Unit
}
