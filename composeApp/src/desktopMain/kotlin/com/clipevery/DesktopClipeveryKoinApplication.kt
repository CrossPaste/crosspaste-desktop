package com.clipevery

import androidx.compose.ui.unit.dp
import com.clipevery.app.AppEnv
import com.clipevery.app.AppFileType
import com.clipevery.app.AppInfo
import com.clipevery.app.AppUI
import com.clipevery.app.DesktopAppInfoFactory
import com.clipevery.clip.ChromeService
import com.clipevery.clip.ClipSearchService
import com.clipevery.clip.ClipboardService
import com.clipevery.clip.DesktopChromeService
import com.clipevery.clip.DesktopClipSearchService
import com.clipevery.clip.DesktopTransferableConsumer
import com.clipevery.clip.TransferableConsumer
import com.clipevery.clip.getDesktopClipboardService
import com.clipevery.clip.plugin.FilesToImagesPlugin
import com.clipevery.clip.plugin.DistinctPlugin
import com.clipevery.clip.plugin.SortPlugin
import com.clipevery.clip.plugin.GenerateUrlPlugin
import com.clipevery.clip.service.FilesItemService
import com.clipevery.clip.service.HtmlItemService
import com.clipevery.clip.service.ImageItemService
import com.clipevery.clip.service.TextItemService
import com.clipevery.clip.service.UrlItemService
import com.clipevery.config.ConfigManager
import com.clipevery.config.DefaultConfigManager
import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.clip.ClipRealm
import com.clipevery.dao.signal.SignalDao
import com.clipevery.dao.signal.SignalRealm
import com.clipevery.dao.sync.SyncRuntimeInfoDao
import com.clipevery.dao.sync.SyncRuntimeInfoRealm
import com.clipevery.dao.task.ClipTaskDao
import com.clipevery.dao.task.ClipTaskRealm
import com.clipevery.dao.task.TaskType
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
import com.clipevery.net.SyncRefresher
import com.clipevery.net.clientapi.DesktopSyncClientApi
import com.clipevery.net.clientapi.DesktopSendClipClientApi
import com.clipevery.net.clientapi.SyncClientApi
import com.clipevery.net.clientapi.SendClipClientApi
import com.clipevery.path.DesktopPathProvider
import com.clipevery.path.PathProvider
import com.clipevery.presist.DesktopFilePersist
import com.clipevery.presist.FilePersist
import com.clipevery.realm.RealmManager
import com.clipevery.realm.RealmManagerImpl
import com.clipevery.signal.DesktopPreKeyStore
import com.clipevery.signal.DesktopSessionStore
import com.clipevery.signal.DesktopSignalProtocolStore
import com.clipevery.signal.DesktopSignedPreKeyStore
import com.clipevery.signal.getClipIdentityKeyStoreFactory
import com.clipevery.sync.DesktopSyncManager
import com.clipevery.sync.SyncManager
import com.clipevery.task.DeleteClipTaskExecutor
import com.clipevery.task.DesktopTaskExecutor
import com.clipevery.task.SyncClipTaskExecutor
import com.clipevery.task.TaskExecutor
import com.clipevery.ui.DesktopThemeDetector
import com.clipevery.ui.ThemeDetector
import com.clipevery.ui.resource.ClipResourceLoader
import com.clipevery.ui.resource.DesktopAbsoluteClipResourceLoader
import com.clipevery.utils.DesktopFileUtils
import com.clipevery.utils.DesktopQRCodeGenerator
import com.clipevery.utils.FileUtils
import com.clipevery.utils.IDGenerator
import com.clipevery.utils.IDGeneratorFactory
import com.clipevery.utils.QRCodeGenerator
import com.clipevery.utils.TelnetUtils
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

        val appEnv = AppEnv.getAppEnv()

        val appModule = module {

            // simple component
            single<AppEnv> { appEnv }
            single<AppInfo> { DesktopAppInfoFactory(get()).createAppInfo() }
            single<EndpointInfoFactory> { DesktopEndpointInfoFactory(lazy { get<ClipServer>() }) }
            single<PathProvider> { DesktopPathProvider }
            single<FilePersist> { DesktopFilePersist }
            single<ConfigManager> { DefaultConfigManager(get<FilePersist>().getPersist("appConfig.json", AppFileType.USER), get()) }
            single<QRCodeGenerator> { DesktopQRCodeGenerator(get(), get()) }
            single<IDGenerator> { IDGeneratorFactory(get()).createIDGenerator() }
            single<FileUtils> { DesktopFileUtils }

            // realm component
            single<RealmManager> { RealmManagerImpl.createRealmManager(get()) }
            single<SignalDao> { SignalRealm(get<RealmManager>().realm) }
            single<SyncRuntimeInfoDao> { SyncRuntimeInfoRealm(get<RealmManager>().realm) }
            single<ClipDao> { ClipRealm(get<RealmManager>().realm, get()) }
            single<ClipTaskDao> { ClipTaskRealm(get<RealmManager>().realm) }

            // net component
            single<ClipClient> { DesktopClipClient(get<AppInfo>()) }
            single<ClipServer> { DesktopClipServer(get<ConfigManager>()).start() }
            single<ClipBonjourService> { DesktopClipBonjourService(get(), get()).registerService() }
            single<TelnetUtils> { TelnetUtils(get<ClipClient>()) }
            single<SyncClientApi> { DesktopSyncClientApi(get()) }
            single<SendClipClientApi> { DesktopSendClipClientApi(get()) }
            single { DesktopSyncManager(get(), get(), get(), get()) }
            single<SyncRefresher> { get<DesktopSyncManager>() }
            single<SyncManager> { get<DesktopSyncManager>() }

            // signal component
            single<IdentityKeyStore> { getClipIdentityKeyStoreFactory(get(), get()).createIdentityKeyStore() }
            single<SessionStore> { DesktopSessionStore(get()) }
            single<PreKeyStore> { DesktopPreKeyStore(get())  }
            single<SignedPreKeyStore> { DesktopSignedPreKeyStore(get()) }
            single<SignalProtocolStore> { DesktopSignalProtocolStore(get(), get(), get(), get()) }

            // clip component
            single<ClipboardService> { getDesktopClipboardService(get()) }
            single<TransferableConsumer> { DesktopTransferableConsumer(
                get(), get(), get(), listOf(
                    FilesItemService(appInfo = get()),
                    HtmlItemService(appInfo = get()),
                    ImageItemService(appInfo = get()),
                    TextItemService(appInfo = get()),
                    UrlItemService(appInfo = get())
                ), listOf(
                    DistinctPlugin,
                    GenerateUrlPlugin,
                    FilesToImagesPlugin,
                    SortPlugin
                )
            ) }
            single<ChromeService> { DesktopChromeService }
            single<ClipSearchService> { DesktopClipSearchService(get()) }
            single<TaskExecutor> { DesktopTaskExecutor(mapOf(
                Pair(TaskType.SYNC_CLIP_TASK, SyncClipTaskExecutor(lazy { get<ClipDao>() }, get(), get())),
                Pair(TaskType.DELETE_CLIP_TASK, DeleteClipTaskExecutor())
            ), get()) }

            // ui component
            single<AppUI> { AppUI(width = 460.dp, height = 710.dp) }
            single<GlobalCopywriter> { GlobalCopywriterImpl(get()) }
            single<GlobalListener> { GlobalListener(get(), get()) }
            single<ThemeDetector> { DesktopThemeDetector(get()) }
            single<ClipResourceLoader> { DesktopAbsoluteClipResourceLoader }
        }
        return GlobalContext.startKoin {
            modules(appModule)
        }
    }

}