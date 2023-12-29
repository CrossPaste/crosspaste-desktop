package com.clipevery

import com.clipevery.app.AppFileType
import com.clipevery.app.AppInfo
import com.clipevery.app.DesktopAppInfoFactory
import com.clipevery.clip.ClipboardService
import com.clipevery.clip.DesktopTransferableConsumer
import com.clipevery.clip.TransferableConsumer
import com.clipevery.clip.getDesktopClipboardService
import com.clipevery.config.ConfigManager
import com.clipevery.config.DefaultConfigManager
import com.clipevery.dao.DriverFactory
import com.clipevery.dao.SignalStoreDao
import com.clipevery.dao.SignalStoreDaoImpl
import com.clipevery.dao.SyncInfoDao
import com.clipevery.dao.SyncInfoDaoImpl
import com.clipevery.dao.createDatabase
import com.clipevery.endpoint.DesktopEndpointInfoFactory
import com.clipevery.endpoint.EndpointInfoFactory
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.i18n.GlobalCopywriterImpl
import com.clipevery.listen.GlobalListener
import com.clipevery.net.ClipBonjourService
import com.clipevery.net.ClipClient
import com.clipevery.net.ClipServer
import com.clipevery.net.DesktopClipBonjourService
import com.clipevery.net.DesktopClipClient
import com.clipevery.net.DesktopClipServer
import com.clipevery.presist.DesktopFilePersist
import com.clipevery.presist.FilePersist
import com.clipevery.signal.DesktopPreKeyStore
import com.clipevery.signal.DesktopSessionStore
import com.clipevery.signal.DesktopSignalProtocolStore
import com.clipevery.signal.DesktopSignedPreKeyStore
import com.clipevery.signal.getClipIdentityKeyStoreFactory
import com.clipevery.ui.DesktopThemeDetector
import com.clipevery.ui.ThemeDetector
import com.clipevery.utils.DesktopQRCodeGenerator
import com.clipevery.utils.QRCodeGenerator
import org.koin.core.KoinApplication
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.PreKeyStore
import org.signal.libsignal.protocol.state.SessionStore
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyStore

object Dependencies {

    val koinApplication: KoinApplication = initKoinApplication()

    private fun initKoinApplication(): KoinApplication {
        val appModule = module {
            single<AppInfo> { DesktopAppInfoFactory(get()).createAppInfo() }
            single<FilePersist> { DesktopFilePersist() }
            single<ConfigManager> { DefaultConfigManager(get<FilePersist>().getPersist("appConfig.json", AppFileType.USER)) }
            single<ClipServer> { DesktopClipServer().start() }
            single<Lazy<ClipServer>> { lazy { get<ClipServer>() } }
            single<ClipClient> { DesktopClipClient() }
            single<EndpointInfoFactory> { DesktopEndpointInfoFactory( lazy { get<ClipServer>() }) }
            single<QRCodeGenerator> { DesktopQRCodeGenerator(get()) }
            single<ClipBonjourService> { DesktopClipBonjourService(get(), get()).registerService() }
            single<GlobalCopywriter> { GlobalCopywriterImpl(get()) }
            single<ClipboardService> { getDesktopClipboardService(get()) }
            single<TransferableConsumer> { DesktopTransferableConsumer() }
            single<GlobalListener> { GlobalListener() }
            single<DriverFactory> { DriverFactory() }
            single<ThemeDetector> { DesktopThemeDetector(get()) }
            single<IdentityKeyStore> { getClipIdentityKeyStoreFactory(get(), get()).createIdentityKeyStore() }
            single<SessionStore> { DesktopSessionStore(get()) }
            single<PreKeyStore> { DesktopPreKeyStore(get())  }
            single<SignedPreKeyStore> { DesktopSignedPreKeyStore(get()) }
            single<SignalProtocolStore> { DesktopSignalProtocolStore(get(), get(), get(), get()) }
            single<Database> { createDatabase(DriverFactory()) }
            single<SyncInfoDao> { SyncInfoDaoImpl(get()) }
            single<SignalStoreDao> { SignalStoreDaoImpl(get()) }
        }
        return GlobalContext.startKoin {
            modules(appModule)
        }
    }

}