package com.crosspaste.net.plugin

import com.crosspaste.secure.SecureStore
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.utils.io.*
import kotlinx.io.readByteArray

class ServerDecryptionPluginFactory(private val secureStore: SecureStore) {

    fun createPlugin(): ApplicationPlugin<PluginConfig> {
        return createApplicationPlugin(
            "ServerDecryptPlugin",
            { PluginConfig(secureStore) },
        ) {
            val logger: KLogger = KotlinLogging.logger {}

            val secureStore: SecureStore = pluginConfig.secureStore

            on(ReceiveRequestBytes) { call, body ->
                val headers = call.request.headers
                headers["appInstanceId"]?.let { appInstanceId ->
                    headers["secure"]?.let {
                        logger.debug { "server decrypt $appInstanceId" }
                        return@on application.writer {
                            logger.info { "xxxxxxxxxxxxx" }
                            val processor = secureStore.getMessageProcessor(appInstanceId)
                            val encryptedContent = body.readRemaining().readByteArray()
                            val decrypted = processor.decrypt(encryptedContent)
                            channel.writeFully(decrypted, 0, decrypted.size)
                        }.channel
                    }
                }
                return@on body
            }
        }
    }
}
