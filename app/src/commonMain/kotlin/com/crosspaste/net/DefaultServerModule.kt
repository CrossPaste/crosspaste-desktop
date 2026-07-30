package com.crosspaste.net

import com.crosspaste.app.AppControl
import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppTokenApi
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.net.plugin.ServerDecryptionPluginFactory
import com.crosspaste.net.plugin.ServerEncryptPluginFactory
import com.crosspaste.net.routing.SyncRoutingApi
import com.crosspaste.net.routing.pairingV3Routing
import com.crosspaste.net.routing.pasteRouting
import com.crosspaste.net.routing.pullRouting
import com.crosspaste.net.routing.pushRouting
import com.crosspaste.net.routing.syncRouting
import com.crosspaste.net.routing.wsRouting
import com.crosspaste.net.ws.WsMessageHandler
import com.crosspaste.net.ws.WsSessionManager
import com.crosspaste.pairing.v3.PairingProtocolV3Service
import com.crosspaste.pairing.v3.PairingVersionCoordinator
import com.crosspaste.paste.CacheManager
import com.crosspaste.paste.PasteReleaseService
import com.crosspaste.paste.PasteboardService
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.secure.SecureStore
import com.crosspaste.sync.NearbyDeviceManager
import com.crosspaste.sync.PastePullService
import com.crosspaste.sync.PendingKeyExchangeStore
import com.crosspaste.sync.PushSessionManager
import com.crosspaste.utils.failResponse
import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.namedScope
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

open class DefaultServerModule(
    private val appControl: AppControl,
    private val appInfo: AppInfo,
    private val appTokenApi: AppTokenApi,
    private val cacheManager: CacheManager,
    private val configManager: CommonConfigManager,
    private val exceptionHandler: ExceptionHandler,
    private val nearbyDeviceManager: NearbyDeviceManager,
    private val networkInterfaceService: NetworkInterfaceService,
    private val pairingProtocolV3Service: PairingProtocolV3Service,
    private val pairingVersionCoordinator: PairingVersionCoordinator,
    private val pendingKeyExchangeStore: PendingKeyExchangeStore,
    private val pasteboardService: PasteboardService,
    private val pasteDao: PasteDao,
    private val pastePullService: PastePullService,
    private val pasteReleaseService: PasteReleaseService,
    private val pushSessionManager: PushSessionManager,
    private val secureKeyPairSerializer: SecureKeyPairSerializer,
    private val secureStore: SecureStore,
    private val syncApi: SyncApi,
    private val syncInfoFactory: SyncInfoFactory,
    private val syncRoutingApi: SyncRoutingApi,
    private val serverEncryptPluginFactory: ServerEncryptPluginFactory,
    private val serverDecryptionPluginFactory: ServerDecryptionPluginFactory,
    private val userDataPathProvider: UserDataPathProvider,
    private val wsMessageHandler: WsMessageHandler,
    private val wsSessionManager: WsSessionManager,
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
            install(io.ktor.server.websocket.WebSockets) {
                pingPeriodMillis = 30_000
                timeoutMillis = 15_000
                maxFrameSize = 1024 * 1024
            }
            install(serverEncryptPluginFactory.createPlugin())
            install(serverDecryptionPluginFactory.createPlugin())
            intercept(ApplicationCallPipeline.Setup) {
                logger.debug {
                    "Received request: ${call.request.httpMethod.value} ${call.request.uri} ${call.request.contentType()}"
                }
            }
            val pasteRoutingScope =
                namedScope(
                    ioDispatcher,
                    "DefaultServerModule.pasteRoutingScope",
                    SupervisorJob(coroutineContext[Job]),
                )
            routing {
                syncRouting(
                    appInfo,
                    appTokenApi,
                    configManager,
                    exceptionHandler,
                    nearbyDeviceManager,
                    networkInterfaceService,
                    pendingKeyExchangeStore,
                    secureKeyPairSerializer,
                    secureStore,
                    syncApi,
                    syncInfoFactory,
                    syncRoutingApi,
                    ::trustSyncInfo,
                    ::releasePendingKeyExchange,
                    pairingVersionCoordinator,
                    pairingProtocolV3Service::hasActiveSession,
                    pairingProtocolV3Service.acceptanceWindow::open,
                )
                pairingV3Routing(
                    pairingProtocolV3Service,
                    pairingVersionCoordinator,
                    ::releasePendingKeyExchange,
                    ::trustSyncInfo,
                )
                pasteRouting(
                    appControl,
                    pasteboardService,
                    pasteReleaseService,
                    pastePullService,
                    pasteRoutingScope,
                    pushSessionManager,
                    syncRoutingApi,
                )
                pullRouting(
                    appInfo,
                    cacheManager,
                    pasteDao,
                    syncRoutingApi,
                    userDataPathProvider,
                )
                pushRouting(
                    appInfo,
                    pushSessionManager,
                    syncRoutingApi,
                    userDataPathProvider,
                )
                wsRouting(
                    appInfo,
                    secureStore,
                    wsSessionManager,
                    wsMessageHandler,
                )
            }
        }

    /**
     * The single release path for a peer's pending v2 key exchange. Every
     * stored exchange owns exactly one token-refresh count and one pending
     * verifier; whoever removes the entry must release both, so all routes
     * (confirm success/expiry, client cancel, v3 takeover) funnel through here.
     */
    private fun releasePendingKeyExchange(appInstanceId: String) {
        if (pendingKeyExchangeStore.remove(appInstanceId)) {
            // releaseVerifier decrements only when the verifier is still
            // pending, so a UI reap that already released this peer (device
            // went offline) cannot be double-counted here.
            appTokenApi.releaseVerifier(appInstanceId)
        }
    }

    private fun trustSyncInfo(
        appInstanceId: String,
        host: String?,
        preRegisteredSyncInfo: SyncInfo?,
    ) {
        // The pre-registered SyncInfo comes from a client-supplied header; the trust
        // decision was made for the AUTHENTICATED appInstanceId, so a header carrying
        // a different identity must never register under it.
        val verifiedPreRegistered =
            preRegisteredSyncInfo?.takeIf { it.appInfo.appInstanceId == appInstanceId }
        if (preRegisteredSyncInfo != null && verifiedPreRegistered == null) {
            logger.warn {
                "trustSyncInfo: dropping pre-registered SyncInfo whose appInstanceId " +
                    "${preRegisteredSyncInfo.appInfo.appInstanceId} does not match authenticated $appInstanceId"
            }
        }
        val syncInfo =
            nearbyDeviceManager.nearbySyncInfos.value
                .firstOrNull {
                    it.appInfo.appInstanceId == appInstanceId
                }
                ?: verifiedPreRegistered
        if (syncInfo != null) {
            syncRoutingApi.trustSyncInfo(syncInfo, host)
        } else {
            logger.warn { "trustSyncInfo: $appInstanceId not found in nearby devices or preRegistered" }
        }
    }
}
