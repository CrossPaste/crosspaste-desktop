package com.clipevery.net.plugin

import com.clipevery.signal.SignalProcessorCache
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import org.signal.libsignal.protocol.message.SignalMessage
import java.nio.ByteBuffer

val SIGNAL_SERVER_DECRYPT_PLUGIN: ApplicationPlugin<SignalConfig> =
    createApplicationPlugin(
        "SignalServerDecryptPlugin",
        ::SignalConfig,
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
                            val processor = signalProcessorCache.getSignalMessageProcessor(appInstanceId)
                            val encryptedContent = body.readRemaining().readBytes()
                            val signalMessage = SignalMessage(encryptedContent)
                            val decrypt = processor.decrypt(signalMessage)
                            channel.writeFully(ByteBuffer.wrap(decrypt))
                        }.channel
                    }
                }
            }
            return@on body
        }
    }
