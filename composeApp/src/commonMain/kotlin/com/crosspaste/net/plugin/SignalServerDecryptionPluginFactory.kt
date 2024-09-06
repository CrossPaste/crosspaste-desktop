package com.crosspaste.net.plugin

import com.crosspaste.signal.SignalProcessorCache
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*

class SignalServerDecryptionPluginFactory(private val signalProcessorCache: SignalProcessorCache) {

    fun createPlugin(): ApplicationPlugin<SignalConfig> {
        return createApplicationPlugin(
            "SignalServerDecryptPlugin",
            { SignalConfig(signalProcessorCache) },
        ) {
            val logger: KLogger = KotlinLogging.logger {}

            val signalProcessorCache: SignalProcessorCache = pluginConfig.signalProcessorCache

            on(ReceiveRequestBytes) { call, body ->
                val headers = call.request.headers
                headers["appInstanceId"]?.let { appInstanceId ->
                    headers["signal"]?.let { signal ->
                        if (signal == "1") {
                            logger.debug { "signal server decrypt $appInstanceId" }
                            return@on application.writer {
                                val processor =
                                    signalProcessorCache.getSignalMessageProcessor(appInstanceId)
                                val encryptedContent = body.readRemaining().readBytes()
                                val decrypted = processor.decryptSignalMessage(encryptedContent)
                                channel.writeFully(decrypted, 0, decrypted.size)
                            }.channel
                        }
                    }
                }
                return@on body
            }
        }
    }
}
