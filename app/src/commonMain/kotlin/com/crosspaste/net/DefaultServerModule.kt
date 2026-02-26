package com.crosspaste.net

import com.crosspaste.app.AppControl
import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppTokenApi
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.net.plugin.ServerDecryptionPluginFactory
import com.crosspaste.net.plugin.ServerEncryptPluginFactory
import com.crosspaste.net.routing.SyncRoutingApi
import com.crosspaste.net.routing.pasteRouting
import com.crosspaste.net.routing.pullRouting
import com.crosspaste.net.routing.syncRouting
import com.crosspaste.paste.CacheManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.secure.SecureStore
import com.crosspaste.sync.NearbyDeviceManager
import com.crosspaste.utils.failResponse
import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

open class DefaultServerModule(
    private val appControl: AppControl,
    private val appInfo: AppInfo,
    private val appTokenApi: AppTokenApi,
    private val cacheManager: CacheManager,
    private val exceptionHandler: ExceptionHandler,
    private val nearbyDeviceManager: NearbyDeviceManager,
    private val networkInterfaceService: NetworkInterfaceService,
    private val pasteboardService: PasteboardService,
    private val pasteDao: PasteDao,
    private val secureKeyPairSerializer: SecureKeyPairSerializer,
    private val secureStore: SecureStore,
    private val syncApi: SyncApi,
    private val syncInfoFactory: SyncInfoFactory,
    private val syncRoutingApi: SyncRoutingApi,
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
                logger.debug {
                    "Received request: ${call.request.httpMethod.value} ${call.request.uri} ${call.request.contentType()}"
                }
            }
            val pasteRoutingScope =
                CoroutineScope(ioDispatcher + SupervisorJob(coroutineContext[Job]))
            routing {
                syncRouting(
                    appInfo,
                    appTokenApi,
                    exceptionHandler,
                    networkInterfaceService,
                    secureKeyPairSerializer,
                    secureStore,
                    syncApi,
                    syncInfoFactory,
                    syncRoutingApi,
                    ::trustSyncInfo,
                )
                pasteRouting(
                    appControl,
                    pasteboardService,
                    pasteRoutingScope,
                    syncRoutingApi,
                )
                pullRouting(
                    appInfo,
                    cacheManager,
                    pasteDao,
                    syncRoutingApi,
                    userDataPathProvider,
                )
            }
        }

    private fun trustSyncInfo(
        appInstanceId: String,
        host: String?,
    ) {
        val syncInfo =
            nearbyDeviceManager.nearbySyncInfos.value
                .firstOrNull {
                    it.appInfo.appInstanceId == appInstanceId
                }
        if (syncInfo != null) {
            syncRoutingApi.trustSyncInfo(syncInfo, host)
        } else {
            logger.warn { "trustSyncInfo: $appInstanceId not found in nearby devices" }
        }
    }
}
