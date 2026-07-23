package com.crosspaste.net

import com.crosspaste.app.AppControl
import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppTokenApi
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.net.plugin.ServerDecryptionPluginFactory
import com.crosspaste.net.plugin.ServerEncryptPluginFactory
import com.crosspaste.net.routing.SyncRoutingApi
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
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*

class DesktopServerModule(
    appControl: AppControl,
    appInfo: AppInfo,
    appTokenApi: AppTokenApi,
    cacheManager: CacheManager,
    configManager: CommonConfigManager,
    exceptionHandler: ExceptionHandler,
    nearbyDeviceManager: NearbyDeviceManager,
    networkInterfaceService: NetworkInterfaceService,
    pairingProtocolV3Service: PairingProtocolV3Service,
    pairingVersionCoordinator: PairingVersionCoordinator,
    pendingKeyExchangeStore: PendingKeyExchangeStore,
    pasteboardService: PasteboardService,
    pasteDao: PasteDao,
    pastePullService: PastePullService,
    pasteReleaseService: PasteReleaseService,
    pushSessionManager: PushSessionManager,
    secureKeyPairSerializer: SecureKeyPairSerializer,
    secureStore: SecureStore,
    syncApi: SyncApi,
    syncInfoFactory: SyncInfoFactory,
    syncRoutingApi: SyncRoutingApi,
    serverEncryptPluginFactory: ServerEncryptPluginFactory,
    serverDecryptionPluginFactory: ServerDecryptionPluginFactory,
    userDataPathProvider: UserDataPathProvider,
    wsMessageHandler: WsMessageHandler,
    wsSessionManager: WsSessionManager,
) : DefaultServerModule(
        appControl,
        appInfo,
        appTokenApi,
        cacheManager,
        configManager,
        exceptionHandler,
        nearbyDeviceManager,
        networkInterfaceService,
        pairingProtocolV3Service,
        pairingVersionCoordinator,
        pendingKeyExchangeStore,
        pasteboardService,
        pasteDao,
        pastePullService,
        pasteReleaseService,
        pushSessionManager,
        secureKeyPairSerializer,
        secureStore,
        syncApi,
        syncInfoFactory,
        syncRoutingApi,
        serverEncryptPluginFactory,
        serverDecryptionPluginFactory,
        userDataPathProvider,
        wsMessageHandler,
        wsSessionManager,
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
