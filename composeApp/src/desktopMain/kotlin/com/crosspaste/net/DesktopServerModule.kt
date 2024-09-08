package com.crosspaste.net

import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppTokenService
import com.crosspaste.app.EndpointInfoFactory
import com.crosspaste.dao.signal.SignalDao
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.net.plugin.SignalServerDecryptionPluginFactory
import com.crosspaste.net.plugin.SignalServerEncryptPluginFactory
import com.crosspaste.paste.CacheManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.signal.PreKeyBundleCodecs
import com.crosspaste.signal.PreKeySignalMessageFactory
import com.crosspaste.signal.SignalProcessorCache
import com.crosspaste.signal.SignalProtocolStoreInterface
import com.crosspaste.sync.SyncManager
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*

class DesktopServerModule(
    appInfo: AppInfo,
    appTokenService: AppTokenService,
    cacheManager: CacheManager,
    endpointInfoFactory: EndpointInfoFactory,
    exceptionHandler: ExceptionHandler,
    pasteboardService: PasteboardService,
    preKeyBundleCodecs: PreKeyBundleCodecs,
    preKeySignalMessageFactory: PreKeySignalMessageFactory,
    signalDao: SignalDao,
    signalProtocolStore: SignalProtocolStoreInterface,
    signalProcessorCache: SignalProcessorCache,
    syncManager: SyncManager,
    signalServerEncryptPluginFactory: SignalServerEncryptPluginFactory,
    signalServerDecryptionPluginFactory: SignalServerDecryptionPluginFactory,
    userDataPathProvider: UserDataPathProvider,
) : DefaultServerModule(
        appInfo,
        appTokenService,
        cacheManager,
        endpointInfoFactory,
        exceptionHandler,
        pasteboardService,
        preKeyBundleCodecs,
        preKeySignalMessageFactory,
        signalDao,
        signalProtocolStore,
        signalProcessorCache,
        syncManager,
        signalServerEncryptPluginFactory,
        signalServerDecryptionPluginFactory,
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
