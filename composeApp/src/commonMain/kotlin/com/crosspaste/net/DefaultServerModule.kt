package com.crosspaste.net

import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppTokenService
import com.crosspaste.app.EndpointInfoFactory
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.net.plugin.ServerDecryptionPluginFactory
import com.crosspaste.net.plugin.ServerEncryptPluginFactory
import com.crosspaste.net.routing.baseSyncRouting
import com.crosspaste.net.routing.pasteRouting
import com.crosspaste.net.routing.pullRouting
import com.crosspaste.net.routing.syncRouting
import com.crosspaste.paste.CacheManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.secure.ECDSASerializer
import com.crosspaste.secure.SecureStore
import com.crosspaste.sync.SyncManager
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
    private val appInfo: AppInfo,
    private val appTokenService: AppTokenService,
    private val cacheManager: CacheManager,
    private val ecdsaSerializer: ECDSASerializer,
    private val endpointInfoFactory: EndpointInfoFactory,
    private val exceptionHandler: ExceptionHandler,
    private val pasteboardService: PasteboardService,
    private val secureStore: SecureStore,
    private val syncManager: SyncManager,
    private val serverEncryptPluginFactory: ServerEncryptPluginFactory,
    private val serverDecryptionPluginFactory: ServerDecryptionPluginFactory,
    private val userDataPathProvider: UserDataPathProvider,
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
                baseSyncRouting(
                    appInfo,
                    endpointInfoFactory,
                    secureStore,
                    syncManager,
                )
                syncRouting(
                    appTokenService,
                    ecdsaSerializer,
                    secureStore,
                )
                pasteRouting(
                    syncManager,
                    pasteboardService,
                )
                pullRouting(
                    cacheManager,
                    syncManager,
                    userDataPathProvider,
                )
            }
        }
}
