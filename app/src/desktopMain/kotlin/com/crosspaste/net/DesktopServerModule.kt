package com.crosspaste.net

import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.net.plugin.ServerDecryptionPluginFactory
import com.crosspaste.net.plugin.ServerEncryptPluginFactory
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*

class DesktopServerModule(
    exceptionHandler: ExceptionHandler,
    serverEncryptPluginFactory: ServerEncryptPluginFactory,
    serverDecryptionPluginFactory: ServerDecryptionPluginFactory,
) : DefaultServerModule(
        exceptionHandler,
        serverEncryptPluginFactory,
        serverDecryptionPluginFactory,
    ) {

    override fun installModules(): Application.() -> Unit =
        {
            install(Compression) {
                gzip {
                    priority = 1.0
                }
                deflate {
                    priority = 10.0
                    minimumSize(1024)
                }
            }
            super.installModules()()
        }
}
