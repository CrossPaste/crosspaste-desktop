package com.crosspaste.net

import com.crosspaste.app.AppControl
import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppTokenApi
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.net.plugin.ServerDecryptionPluginFactory
import com.crosspaste.net.plugin.ServerEncryptPluginFactory
import com.crosspaste.net.routing.SyncRoutingApi
import com.crosspaste.paste.CacheManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.secure.SecureStore
import com.crosspaste.sync.NearbyDeviceManager
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*

class DesktopServerModule(
    appControl: AppControl,
    appInfo: AppInfo,
    appTokenApi: AppTokenApi,
    cacheManager: CacheManager,
    exceptionHandler: ExceptionHandler,
    nearbyDeviceManager: NearbyDeviceManager,
    networkInterfaceService: NetworkInterfaceService,
    pasteboardService: PasteboardService,
    pasteDao: PasteDao,
    secureKeyPairSerializer: SecureKeyPairSerializer,
    secureStore: SecureStore,
    syncApi: SyncApi,
    syncInfoFactory: SyncInfoFactory,
    syncRoutingApi: SyncRoutingApi,
    serverEncryptPluginFactory: ServerEncryptPluginFactory,
    serverDecryptionPluginFactory: ServerDecryptionPluginFactory,
    userDataPathProvider: UserDataPathProvider,
) : DefaultServerModule(
        appControl,
        appInfo,
        appTokenApi,
        cacheManager,
        exceptionHandler,
        nearbyDeviceManager,
        networkInterfaceService,
        pasteboardService,
        pasteDao,
        secureKeyPairSerializer,
        secureStore,
        syncApi,
        syncInfoFactory,
        syncRoutingApi,
        serverEncryptPluginFactory,
        serverDecryptionPluginFactory,
        userDataPathProvider,
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
