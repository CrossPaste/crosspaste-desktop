package com.crosspaste.net

import com.crosspaste.app.AppControl
import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppTokenApi
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.net.cli.CliTokenManager
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
    cliTokenManager: CliTokenManager,
    configManager: CommonConfigManager,
    exceptionHandler: ExceptionHandler,
    nearbyDeviceManager: NearbyDeviceManager,
    networkInterfaceService: NetworkInterfaceService,
    pasteboardService: PasteboardService,
    pasteDao: PasteDao,
    secureKeyPairSerializer: SecureKeyPairSerializer,
    secureStore: SecureStore,
    server: Lazy<Server>,
    syncApi: SyncApi,
    syncInfoFactory: SyncInfoFactory,
    syncRuntimeInfoDao: SyncRuntimeInfoDao,
    syncRoutingApi: SyncRoutingApi,
    serverEncryptPluginFactory: ServerEncryptPluginFactory,
    serverDecryptionPluginFactory: ServerDecryptionPluginFactory,
    userDataPathProvider: UserDataPathProvider,
) : DefaultServerModule(
        appControl,
        appInfo,
        appTokenApi,
        cacheManager,
        cliTokenManager,
        configManager,
        exceptionHandler,
        nearbyDeviceManager,
        networkInterfaceService,
        pasteboardService,
        pasteDao,
        secureKeyPairSerializer,
        secureStore,
        server,
        syncApi,
        syncInfoFactory,
        syncRuntimeInfoDao,
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
