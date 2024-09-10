package com.crosspaste.net

import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppTokenService
import com.crosspaste.app.EndpointInfoFactory
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.net.plugin.SignalServerDecryptionPluginFactory
import com.crosspaste.net.plugin.SignalServerEncryptPluginFactory
import com.crosspaste.net.routing.baseSyncRouting
import com.crosspaste.net.routing.pasteRouting
import com.crosspaste.net.routing.pullRouting
import com.crosspaste.net.routing.syncRouting
import com.crosspaste.paste.CacheManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.realm.signal.SignalRealm
import com.crosspaste.signal.PreKeyBundleCodecs
import com.crosspaste.signal.PreKeySignalMessageFactory
import com.crosspaste.signal.SignalProcessorCache
import com.crosspaste.signal.SignalProtocolStoreInterface
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
    private val endpointInfoFactory: EndpointInfoFactory,
    private val exceptionHandler: ExceptionHandler,
    private val pasteboardService: PasteboardService,
    private val preKeyBundleCodecs: PreKeyBundleCodecs,
    private val preKeySignalMessageFactory: PreKeySignalMessageFactory,
    private val signalRealm: SignalRealm,
    private val signalProtocolStore: SignalProtocolStoreInterface,
    private val signalProcessorCache: SignalProcessorCache,
    private val syncManager: SyncManager,
    private val signalServerEncryptPluginFactory: SignalServerEncryptPluginFactory,
    private val signalServerDecryptionPluginFactory: SignalServerDecryptionPluginFactory,
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
            install(signalServerEncryptPluginFactory.createPlugin())
            install(signalServerDecryptionPluginFactory.createPlugin())
            intercept(ApplicationCallPipeline.Setup) {
                logger.info { "Received request: ${call.request.httpMethod.value} ${call.request.uri} ${call.request.contentType()}" }
            }
            routing {
                baseSyncRouting(
                    appInfo,
                    endpointInfoFactory,
                    signalProcessorCache,
                    syncManager,
                )
                syncRouting(
                    appInfo,
                    appTokenService,
                    preKeyBundleCodecs,
                    preKeySignalMessageFactory,
                    signalRealm,
                    signalProtocolStore,
                    signalProcessorCache,
                    syncManager,
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
