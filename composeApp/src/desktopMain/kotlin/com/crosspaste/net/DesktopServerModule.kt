package com.crosspaste.net

import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppTokenService
import com.crosspaste.app.EndpointInfoFactory
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.net.plugin.ServerDecryptionPluginFactory
import com.crosspaste.net.plugin.ServerEncryptPluginFactory
import com.crosspaste.paste.CacheManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.secure.SecureStore
import com.crosspaste.sync.SyncManager
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*

class DesktopServerModule(
    appInfo: AppInfo,
    appTokenService: AppTokenService,
    cacheManager: CacheManager,
    secureKeyPairSerializer: SecureKeyPairSerializer,
    endpointInfoFactory: EndpointInfoFactory,
    exceptionHandler: ExceptionHandler,
    pasteboardService: PasteboardService,
    secureStore: SecureStore,
    syncManager: SyncManager,
    serverEncryptPluginFactory: ServerEncryptPluginFactory,
    serverDecryptionPluginFactory: ServerDecryptionPluginFactory,
    userDataPathProvider: UserDataPathProvider,
) : DefaultServerModule(
        appInfo,
        appTokenService,
        cacheManager,
        endpointInfoFactory,
        exceptionHandler,
        pasteboardService,
        secureKeyPairSerializer,
        secureStore,
        syncManager,
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
