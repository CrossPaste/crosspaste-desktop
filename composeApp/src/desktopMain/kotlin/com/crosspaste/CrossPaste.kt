package com.crosspaste

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.application
import com.crosspaste.app.AppEnv
import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppInfoFactory
import com.crosspaste.app.AppLaunchState
import com.crosspaste.app.AppLock
import com.crosspaste.app.AppRestartService
import com.crosspaste.app.AppStartUpService
import com.crosspaste.app.AppTokenService
import com.crosspaste.app.AppWindowManager
import com.crosspaste.app.DesktopAppInfoFactory
import com.crosspaste.app.DesktopAppLaunch
import com.crosspaste.app.DesktopAppRestartService
import com.crosspaste.app.DesktopAppStartUpService
import com.crosspaste.app.DesktopAppTokenService
import com.crosspaste.app.getDesktopAppWindowManager
import com.crosspaste.clean.CleanPasteScheduler
import com.crosspaste.clean.DesktopCleanPasteScheduler
import com.crosspaste.config.ConfigManager
import com.crosspaste.config.DefaultConfigManager
import com.crosspaste.dao.paste.PasteDao
import com.crosspaste.dao.paste.PasteRealm
import com.crosspaste.dao.signal.SignalDao
import com.crosspaste.dao.signal.SignalRealm
import com.crosspaste.dao.sync.SyncRuntimeInfoDao
import com.crosspaste.dao.sync.SyncRuntimeInfoRealm
import com.crosspaste.dao.task.PasteTaskDao
import com.crosspaste.dao.task.PasteTaskRealm
import com.crosspaste.endpoint.DesktopEndpointInfoFactory
import com.crosspaste.endpoint.EndpointInfoFactory
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.i18n.GlobalCopywriterImpl
import com.crosspaste.listen.ActiveGraphicsDevice
import com.crosspaste.listen.DesktopGlobalListener
import com.crosspaste.listen.DesktopMouseListener
import com.crosspaste.listen.DesktopShortKeysAction
import com.crosspaste.listen.DesktopShortcutKeys
import com.crosspaste.listen.DesktopShortcutKeysListener
import com.crosspaste.listen.DesktopShortcutKeysLoader
import com.crosspaste.listen.ShortcutKeysLoader
import com.crosspaste.listener.GlobalListener
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.listener.ShortcutKeysAction
import com.crosspaste.listener.ShortcutKeysListener
import com.crosspaste.log.CrossPasteLogger
import com.crosspaste.log.initLogger
import com.crosspaste.net.DesktopFaviconLoader
import com.crosspaste.net.DesktopPasteBonjourService
import com.crosspaste.net.DesktopPasteClient
import com.crosspaste.net.DesktopPasteServer
import com.crosspaste.net.DesktopSyncInfoFactory
import com.crosspaste.net.FaviconLoader
import com.crosspaste.net.PasteBonjourService
import com.crosspaste.net.PasteClient
import com.crosspaste.net.PasteServer
import com.crosspaste.net.SyncInfoFactory
import com.crosspaste.net.SyncRefresher
import com.crosspaste.net.clientapi.DesktopPullClientApi
import com.crosspaste.net.clientapi.DesktopSendPasteClientApi
import com.crosspaste.net.clientapi.DesktopSyncClientApi
import com.crosspaste.net.clientapi.PullClientApi
import com.crosspaste.net.clientapi.SendPasteClientApi
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.net.plugin.SignalClientDecryptPlugin
import com.crosspaste.net.plugin.SignalClientEncryptPlugin
import com.crosspaste.paste.CacheManager
import com.crosspaste.paste.CacheManagerImpl
import com.crosspaste.paste.ChromeService
import com.crosspaste.paste.DesktopChromeService
import com.crosspaste.paste.DesktopPastePreviewService
import com.crosspaste.paste.DesktopPasteSearchService
import com.crosspaste.paste.DesktopPasteSyncProcessManager
import com.crosspaste.paste.DesktopTransferableConsumer
import com.crosspaste.paste.DesktopTransferableProducer
import com.crosspaste.paste.PastePreviewService
import com.crosspaste.paste.PasteSearchService
import com.crosspaste.paste.PasteSyncProcessManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.TransferableConsumer
import com.crosspaste.paste.TransferableProducer
import com.crosspaste.paste.getDesktopPasteboardService
import com.crosspaste.paste.plugin.DistinctPlugin
import com.crosspaste.paste.plugin.FilesToImagesPlugin
import com.crosspaste.paste.plugin.GenerateUrlPlugin
import com.crosspaste.paste.plugin.RemoveFolderImagePlugin
import com.crosspaste.paste.plugin.SortPlugin
import com.crosspaste.paste.service.FilesItemService
import com.crosspaste.paste.service.HtmlItemService
import com.crosspaste.paste.service.ImageItemService
import com.crosspaste.paste.service.TextItemService
import com.crosspaste.paste.service.UrlItemService
import com.crosspaste.path.DesktopPathProvider
import com.crosspaste.path.PathProvider
import com.crosspaste.platform.currentPlatform
import com.crosspaste.presist.DesktopFilePersist
import com.crosspaste.presist.FilePersist
import com.crosspaste.realm.RealmManager
import com.crosspaste.realm.RealmManagerImpl
import com.crosspaste.signal.DesktopPreKeyStore
import com.crosspaste.signal.DesktopSessionStore
import com.crosspaste.signal.DesktopSignalProtocolStore
import com.crosspaste.signal.DesktopSignedPreKeyStore
import com.crosspaste.signal.SignalProcessorCache
import com.crosspaste.signal.SignalProcessorCacheImpl
import com.crosspaste.signal.getPasteIdentityKeyStoreFactory
import com.crosspaste.sync.DesktopDeviceManager
import com.crosspaste.sync.DesktopQRCodeGenerator
import com.crosspaste.sync.DesktopSyncManager
import com.crosspaste.sync.DeviceManager
import com.crosspaste.sync.SyncManager
import com.crosspaste.task.CleanPasteTaskExecutor
import com.crosspaste.task.DeletePasteTaskExecutor
import com.crosspaste.task.DesktopTaskExecutor
import com.crosspaste.task.Html2ImageTaskExecutor
import com.crosspaste.task.PullFileTaskExecutor
import com.crosspaste.task.PullIconTaskExecutor
import com.crosspaste.task.SyncPasteTaskExecutor
import com.crosspaste.task.TaskExecutor
import com.crosspaste.ui.CrossPasteMainWindow
import com.crosspaste.ui.CrossPasteSearchWindow
import com.crosspaste.ui.DesktopThemeDetector
import com.crosspaste.ui.GrantAccessibilityPermissionsWindow
import com.crosspaste.ui.LinuxTrayView.setWindowPosition
import com.crosspaste.ui.MacTray
import com.crosspaste.ui.PageViewContext
import com.crosspaste.ui.PageViewType
import com.crosspaste.ui.ThemeDetector
import com.crosspaste.ui.WindowsTray
import com.crosspaste.ui.base.DesktopDialogService
import com.crosspaste.ui.base.DesktopIconStyle
import com.crosspaste.ui.base.DesktopNotificationManager
import com.crosspaste.ui.base.DesktopToastManager
import com.crosspaste.ui.base.DesktopUISupport
import com.crosspaste.ui.base.DialogService
import com.crosspaste.ui.base.IconStyle
import com.crosspaste.ui.base.NotificationManager
import com.crosspaste.ui.base.ToastManager
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.resource.DesktopAbsolutePasteResourceLoader
import com.crosspaste.ui.resource.PasteResourceLoader
import com.crosspaste.utils.GlobalCoroutineScope
import com.crosspaste.utils.GlobalCoroutineScopeImpl
import com.crosspaste.utils.IDGenerator
import com.crosspaste.utils.IDGeneratorFactory
import com.crosspaste.utils.QRCodeGenerator
import com.crosspaste.utils.TelnetUtils
import com.crosspaste.utils.ioDispatcher
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import com.github.kwhat.jnativehook.mouse.NativeMouseListener
import dorkbox.systemTray.SystemTray
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import org.koin.core.KoinApplication
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.mongodb.kbson.ObjectId
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.PreKeyStore
import org.signal.libsignal.protocol.state.SessionStore
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyStore
import kotlin.io.path.pathString
import kotlin.system.exitProcess

class CrossPaste {

    companion object {

        private val appEnv = AppEnv.CURRENT

        private val crossPasteLogger =
            initLogger(
                DesktopPathProvider.resolve("crosspaste.log", AppFileType.LOG).pathString,
            )

        private val logger: KLogger = KotlinLogging.logger {}

        val koinApplication: KoinApplication = initKoinApplication()

        private fun initKoinApplication(): KoinApplication {
            val appModule =
                module {
                    // simple component
                    single<AppEnv> { appEnv }
                    single<AppInfoFactory> { DesktopAppInfoFactory(get()) }
                    single<AppInfo> { get<AppInfoFactory>().createAppInfo() }
                    single<AppLock> { DesktopAppLaunch }
                    single<AppLaunchState> { DesktopAppLaunch.launch() }
                    single<AppStartUpService> { DesktopAppStartUpService(get()) }
                    single<AppRestartService> { DesktopAppRestartService }
                    single<EndpointInfoFactory> { DesktopEndpointInfoFactory(lazy { get<PasteServer>() }) }
                    single<GlobalCoroutineScope> { GlobalCoroutineScopeImpl }
                    single<SyncInfoFactory> { DesktopSyncInfoFactory(get(), get()) }
                    single<PathProvider> { DesktopPathProvider }
                    single<FilePersist> { DesktopFilePersist }
                    single<ConfigManager> {
                        DefaultConfigManager(
                            get<FilePersist>().getPersist("appConfig.json", AppFileType.USER),
                        )
                    }
                    single<QRCodeGenerator> { DesktopQRCodeGenerator(get(), get()) }
                    single<IDGenerator> { IDGeneratorFactory(get()).createIDGenerator() }
                    single<CacheManager> { CacheManagerImpl(get()) }
                    single<CrossPasteLogger> { crossPasteLogger }
                    single<KLogger> { CrossPaste.logger }

                    // realm component
                    single<RealmManager> { RealmManagerImpl.createRealmManager(get()) }
                    single<SignalDao> { SignalRealm(get<RealmManager>().realm) }
                    single<SyncRuntimeInfoDao> { SyncRuntimeInfoRealm(get<RealmManager>().realm) }
                    single<PasteDao> { PasteRealm(get<RealmManager>().realm, get(), lazy { get() }) }
                    single<PasteTaskDao> { PasteTaskRealm(get<RealmManager>().realm) }

                    // net component
                    single<PasteClient> { DesktopPasteClient(get<AppInfo>(), get(), get()) }
                    single<PasteServer> { DesktopPasteServer(get<ConfigManager>()) }
                    single<PasteBonjourService> { DesktopPasteBonjourService(get(), get(), get()) }
                    single<TelnetUtils> { TelnetUtils(get<PasteClient>()) }
                    single<SyncClientApi> { DesktopSyncClientApi(get(), get()) }
                    single<SendPasteClientApi> { DesktopSendPasteClientApi(get(), get()) }
                    single<PullClientApi> { DesktopPullClientApi(get(), get()) }
                    single { DesktopSyncManager(get(), get(), get(), get(), get(), get(), get(), lazy { get() }) }
                    single<SyncRefresher> { get<DesktopSyncManager>() }
                    single<SyncManager> { get<DesktopSyncManager>() }
                    single<DeviceManager> { DesktopDeviceManager(get(), get(), get()) }
                    single<FaviconLoader> { DesktopFaviconLoader }

                    // signal component
                    single<IdentityKeyStore> { getPasteIdentityKeyStoreFactory(get(), get()).createIdentityKeyStore() }
                    single<SessionStore> { DesktopSessionStore(get()) }
                    single<PreKeyStore> { DesktopPreKeyStore(get()) }
                    single<SignedPreKeyStore> { DesktopSignedPreKeyStore(get()) }
                    single<SignalProtocolStore> { DesktopSignalProtocolStore(get(), get(), get(), get()) }
                    single<SignalProcessorCache> { SignalProcessorCacheImpl(get()) }
                    single<SignalClientEncryptPlugin> { SignalClientEncryptPlugin(get()) }
                    single<SignalClientDecryptPlugin> { SignalClientDecryptPlugin(get()) }

                    // paste component
                    single<PasteboardService> { getDesktopPasteboardService(get(), get(), get(), get(), get()) }
                    single<TransferableConsumer> {
                        DesktopTransferableConsumer(
                            get(),
                            get(),
                            get(),
                            listOf(
                                FilesItemService(appInfo = get()),
                                HtmlItemService(appInfo = get()),
                                ImageItemService(appInfo = get()),
                                TextItemService(appInfo = get()),
                                UrlItemService(appInfo = get()),
                            ),
                            listOf(
                                DistinctPlugin,
                                GenerateUrlPlugin,
                                FilesToImagesPlugin,
                                RemoveFolderImagePlugin,
                                SortPlugin,
                            ),
                        )
                    }
                    single<TransferableProducer> { DesktopTransferableProducer() }
                    single<ChromeService> { DesktopChromeService(get()) }
                    single<PastePreviewService> { DesktopPastePreviewService(get()) }
                    single<PasteSyncProcessManager<ObjectId>> { DesktopPasteSyncProcessManager() }
                    single<PasteSearchService> { DesktopPasteSearchService(get(), get(), get()) }
                    single<CleanPasteScheduler> { DesktopCleanPasteScheduler(get(), get(), get()) }
                    single<TaskExecutor> {
                        DesktopTaskExecutor(
                            listOf(
                                SyncPasteTaskExecutor(get(), get(), get()),
                                DeletePasteTaskExecutor(get()),
                                PullFileTaskExecutor(get(), get(), get(), get(), get()),
                                CleanPasteTaskExecutor(get(), get()),
                                Html2ImageTaskExecutor(get(), get(), get()),
                                PullIconTaskExecutor(get(), get(), get(), get()),
                            ),
                            get(),
                        )
                    }

                    // ui component
                    single<AppWindowManager> { getDesktopAppWindowManager(lazy { get() }, get()) }
                    single<AppTokenService> { DesktopAppTokenService() }
                    single<GlobalCopywriter> { GlobalCopywriterImpl(get()) }
                    single<DesktopShortcutKeysListener> { DesktopShortcutKeysListener(get()) }
                    single<ShortcutKeysListener> { get<DesktopShortcutKeysListener>() }
                    single<NativeKeyListener> { get<DesktopShortcutKeysListener>() }
                    single<DesktopMouseListener> { DesktopMouseListener }
                    single<NativeMouseListener> { get<DesktopMouseListener>() }
                    single<ActiveGraphicsDevice> { get<DesktopMouseListener>() }
                    single<GlobalListener> { DesktopGlobalListener(get(), get(), get(), get(), get()) }
                    single<ThemeDetector> { DesktopThemeDetector(get()) }
                    single<PasteResourceLoader> { DesktopAbsolutePasteResourceLoader }
                    single<ToastManager> { DesktopToastManager() }
                    single<NotificationManager> { DesktopNotificationManager }
                    single<IconStyle> { DesktopIconStyle }
                    single<UISupport> { DesktopUISupport(get(), get()) }
                    single<ShortcutKeys> { DesktopShortcutKeys(get(), get()) }
                    single<ShortcutKeysLoader> { DesktopShortcutKeysLoader(get()) }
                    single<ShortcutKeysAction> { DesktopShortKeysAction(get(), get(), get(), get(), get(), get(), get()) }
                    single<DialogService> { DesktopDialogService() }
                }
            return GlobalContext.startKoin {
                modules(appModule)
            }
        }

        @Throws(Exception::class)
        private fun initInject() {
            try {
                val appLaunchState = koinApplication.koin.get<AppLaunchState>()
                if (appLaunchState.acquireLock) {
                    if (koinApplication.koin.get<ConfigManager>().config.enablePasteboardListening) {
                        koinApplication.koin.get<PasteboardService>().start()
                    }
                    koinApplication.koin.get<QRCodeGenerator>()
                    koinApplication.koin.get<PasteServer>().start()
                    koinApplication.koin.get<PasteClient>()
                    // bonjour service should be registered after paste server started
                    // only server started, bonjour service can get the port
                    koinApplication.koin.get<PasteBonjourService>().registerService()
                    koinApplication.koin.get<CleanPasteScheduler>().start()
                    koinApplication.koin.get<AppStartUpService>().followConfig()
                } else {
                    exitProcess(0)
                }
            } catch (throwable: Throwable) {
                logger.error(throwable) { "cant start crosspaste" }
                exitProcess(0)
            }
        }

        private fun exitCrossPasteApplication(exitApplication: () -> Unit) {
            koinApplication.koin.get<AppLock>().releaseLock()
            koinApplication.koin.get<ChromeService>().quit()
            koinApplication.koin.get<PasteboardService>().stop()
            koinApplication.koin.get<PasteBonjourService>().unregisterService()
            koinApplication.koin.get<PasteServer>().stop()
            koinApplication.koin.get<SyncManager>().notifyExit()
            koinApplication.koin.get<CleanPasteScheduler>().stop()
            koinApplication.koin.get<GlobalListener>().stop()
            exitApplication()
        }

        @JvmStatic
        fun main(args: Array<String>) {
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                logger.error(throwable) { "Uncaught exception in thread: $thread" }
            }

            logger.info { "Starting CrossPaste" }
            initInject()
            logger.info { "CrossPaste started" }

            val appLaunchState = koinApplication.koin.get<AppLaunchState>()
            val appWindowManager = koinApplication.koin.get<AppWindowManager>()
            val platform = currentPlatform()

            val isMacos = platform.isMacos()
            val isWindows = platform.isWindows()
            val isLinux = platform.isLinux()

            val systemTray: SystemTray? =
                if (platform.isLinux()) {
                    SystemTray.get() ?: throw RuntimeException("Unable to load SystemTray!")
                } else {
                    null
                }

            application {
                val ioScope = rememberCoroutineScope { ioDispatcher }

                val exitApplication: () -> Unit = {
                    appWindowManager.showMainWindow = false
                    appWindowManager.showSearchWindow = false
                    ioScope.launch(CoroutineName("ExitApplication")) {
                        exitCrossPasteApplication { exitApplication() }
                    }
                }

                val currentPageViewContext = remember { mutableStateOf(PageViewContext(PageViewType.PASTE_PREVIEW)) }

                CompositionLocalProvider(
                    LocalKoinApplication provides koinApplication,
                    LocalExitApplication provides exitApplication,
                    LocalPageViewContent provides currentPageViewContext,
                ) {
                    val windowIcon: Painter? =
                        if (platform.isMacos()) {
                            painterResource("icon/crosspaste.mac.png")
                        } else if (platform.isWindows()) {
                            painterResource("icon/crosspaste.win.png")
                        } else if (platform.isLinux()) {
                            painterResource("icon/crosspaste.linux.png")
                        } else {
                            null
                        }

                    if (appLaunchState.accessibilityPermissions) {
                        if (isMacos) {
                            MacTray()
                        } else if (isWindows) {
                            WindowsTray()
                        } else if (isLinux) {
                            setWindowPosition(appWindowManager)
                        }

                        CrossPasteMainWindow(exitApplication, systemTray, windowIcon)

                        CrossPasteSearchWindow(windowIcon)
                    } else {
                        GrantAccessibilityPermissionsWindow(windowIcon)
                    }
                }
            }
        }
    }
}
