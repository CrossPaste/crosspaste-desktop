package com.crosspaste.net

import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.net.plugin.ServerDecryptionPluginFactory
import com.crosspaste.net.plugin.ServerEncryptPluginFactory
import com.crosspaste.net.routing.pasteRouting
import com.crosspaste.net.routing.pullRouting
import com.crosspaste.net.routing.syncRouting
import com.crosspaste.utils.failResponse
import com.crosspaste.utils.getJsonUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

open class DefaultServerModule(
    private val exceptionHandler: ExceptionHandler,
    private val serverEncryptPluginFactory: ServerEncryptPluginFactory,
    private val serverDecryptionPluginFactory: ServerDecryptionPluginFactory,
) : ServerModule {

    private val logger = KotlinLogging.logger {}

    override fun installModules(): Application.() -> Unit =
        {
            install(ContentNegotiation) {
                json(getJsonUtils().JSON)
            }
            install(StatusPages) {
                exception(Exception::class) { call, cause ->
                    logger.error(cause) { "Unhandled exception" }
                    failResponse(call, StandardErrorCode.UNKNOWN_ERROR.toErrorCode())
                }
                exceptionHandler.handler()()
            }
            install(serverEncryptPluginFactory.createPlugin())
            install(serverDecryptionPluginFactory.createPlugin())
            intercept(ApplicationCallPipeline.Setup) {
                logger.info { "Received request: ${call.request.httpMethod.value} ${call.request.uri} ${call.request.contentType()}" }
            }
            routing {
                syncRouting()
                pasteRouting()
                pullRouting()
            }
        }
}
