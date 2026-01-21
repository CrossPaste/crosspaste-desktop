package com.crosspaste.net

import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppTokenApi
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.net.plugin.ServerDecryptionPluginFactory
import com.crosspaste.net.plugin.ServerEncryptPluginFactory
import com.crosspaste.net.routing.SyncRoutingApi
import com.crosspaste.net.routing.syncRouting
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.getJsonUtils
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*

class TestServerModule(
    private val appInfo: AppInfo,
    private val appTokenApi: AppTokenApi,
    private val exceptionHandler: ExceptionHandler,
    private val networkInterfaceService: NetworkInterfaceService,
    private val secureKeyPairSerializer: SecureKeyPairSerializer,
    private val secureStore: SecureStore,
    private val serverEncryptPluginFactory: ServerEncryptPluginFactory,
    private val serverDecryptionPluginFactory: ServerDecryptionPluginFactory,
    private val syncApi: SyncApi,
    private val syncInfoFactory: SyncInfoFactory,
    private val syncRoutingApi: SyncRoutingApi,
) : ServerModule {
    override fun installModules(): Application.() -> Unit =
        {
            install(ContentNegotiation) {
                json(getJsonUtils().JSON)
            }
            install(serverEncryptPluginFactory.createPlugin())
            install(serverDecryptionPluginFactory.createPlugin())
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
                ) { _, _ ->
                }
            }
        }
}
