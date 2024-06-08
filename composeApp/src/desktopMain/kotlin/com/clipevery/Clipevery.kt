package com.clipevery

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.clipevery.app.AppEnv
import com.clipevery.app.AppFileType
import com.clipevery.app.AppInfo
import com.clipevery.app.AppLock
import com.clipevery.app.AppRestartService
import com.clipevery.app.AppStartUpService
import com.clipevery.app.AppWindowManager
import com.clipevery.app.DesktopAppInfoFactory
import com.clipevery.app.DesktopAppLock
import com.clipevery.app.DesktopAppRestartService
import com.clipevery.app.DesktopAppStartUpService
import com.clipevery.app.DesktopAppWindowManager
import com.clipevery.app.DesktopAppWindowManager.Companion.MAIN_WINDOW_TITLE
import com.clipevery.app.DesktopAppWindowManager.Companion.SEARCH_WINDOW_TITLE
import com.clipevery.clean.CleanClipScheduler
import com.clipevery.clean.DesktopCleanClipScheduler
import com.clipevery.clip.CacheManager
import com.clipevery.clip.CacheManagerImpl
import com.clipevery.clip.ChromeService
import com.clipevery.clip.ClipPreviewService
import com.clipevery.clip.ClipSearchService
import com.clipevery.clip.ClipSyncProcessManager
import com.clipevery.clip.ClipboardService
import com.clipevery.clip.DesktopChromeService
import com.clipevery.clip.DesktopClipPreviewService
import com.clipevery.clip.DesktopClipSearchService
import com.clipevery.clip.DesktopClipSyncProcessManager
import com.clipevery.clip.DesktopTransferableConsumer
import com.clipevery.clip.DesktopTransferableProducer
import com.clipevery.clip.TransferableConsumer
import com.clipevery.clip.TransferableProducer
import com.clipevery.clip.getDesktopClipboardService
import com.clipevery.clip.plugin.DistinctPlugin
import com.clipevery.clip.plugin.FilesToImagesPlugin
import com.clipevery.clip.plugin.GenerateUrlPlugin
import com.clipevery.clip.plugin.RemoveFolderImagePlugin
import com.clipevery.clip.plugin.SortPlugin
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
import com.clipevery.endpoint.DesktopEndpointInfoFactory
import com.clipevery.endpoint.EndpointInfoFactory
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.i18n.GlobalCopywriterImpl
import com.clipevery.listen.DesktopGlobalListener
import com.clipevery.listen.DesktopShortKeysAction
import com.clipevery.listen.DesktopShortcutKeys
import com.clipevery.listen.DesktopShortcutKeysListener
import com.clipevery.listen.DesktopShortcutKeysLoader
import com.clipevery.listen.ShortcutKeysLoader
import com.clipevery.listener.GlobalListener
import com.clipevery.listener.ShortcutKeys
import com.clipevery.listener.ShortcutKeysAction
import com.clipevery.listener.ShortcutKeysListener
import com.clipevery.log.ClipeveryLogger
import com.clipevery.log.initLogger
import com.clipevery.net.ClipBonjourService
import com.clipevery.net.ClipClient
import com.clipevery.net.ClipServer
import com.clipevery.net.DesktopClipBonjourService
import com.clipevery.net.DesktopClipClient
import com.clipevery.net.DesktopClipServer
import com.clipevery.net.DesktopFaviconLoader
import com.clipevery.net.DesktopSyncInfoFactory
import com.clipevery.net.FaviconLoader
import com.clipevery.net.SyncInfoFactory
import com.clipevery.net.SyncRefresher
import com.clipevery.net.clientapi.DesktopPullClientApi
import com.clipevery.net.clientapi.DesktopSendClipClientApi
import com.clipevery.net.clientapi.DesktopSyncClientApi
import com.clipevery.net.clientapi.PullClientApi
import com.clipevery.net.clientapi.SendClipClientApi
import com.clipevery.net.clientapi.SyncClientApi
import com.clipevery.path.DesktopPathProvider
import com.clipevery.path.PathProvider
import com.clipevery.platform.currentPlatform
import com.clipevery.presist.DesktopFilePersist
import com.clipevery.presist.FilePersist
import com.clipevery.realm.RealmManager
import com.clipevery.realm.RealmManagerImpl
import com.clipevery.signal.DesktopPreKeyStore
import com.clipevery.signal.DesktopSessionStore
import com.clipevery.signal.DesktopSignalProtocolStore
import com.clipevery.signal.DesktopSignedPreKeyStore
import com.clipevery.signal.getClipIdentityKeyStoreFactory
import com.clipevery.sync.DesktopDeviceManager
import com.clipevery.sync.DesktopQRCodeGenerator
import com.clipevery.sync.DesktopSyncManager
import com.clipevery.sync.DeviceManager
import com.clipevery.sync.SyncManager
import com.clipevery.task.CleanClipTaskExecutor
import com.clipevery.task.DeleteClipTaskExecutor
import com.clipevery.task.DesktopTaskExecutor
import com.clipevery.task.Html2ImageTaskExecutor
import com.clipevery.task.PullFileTaskExecutor
import com.clipevery.task.PullIconTaskExecutor
import com.clipevery.task.SyncClipTaskExecutor
import com.clipevery.task.TaskExecutor
import com.clipevery.ui.DesktopThemeDetector
import com.clipevery.ui.LinuxTrayWindowState
import com.clipevery.ui.MacTray
import com.clipevery.ui.PageViewContext
import com.clipevery.ui.PageViewType
import com.clipevery.ui.ThemeDetector
import com.clipevery.ui.WindowsTray
import com.clipevery.ui.base.DesktopDialogService
import com.clipevery.ui.base.DesktopIconStyle
import com.clipevery.ui.base.DesktopMessageManager
import com.clipevery.ui.base.DesktopNotificationManager
import com.clipevery.ui.base.DesktopToastManager
import com.clipevery.ui.base.DesktopUISupport
import com.clipevery.ui.base.DialogService
import com.clipevery.ui.base.IconStyle
import com.clipevery.ui.base.MessageManager
import com.clipevery.ui.base.NotificationManager
import com.clipevery.ui.base.ToastManager
import com.clipevery.ui.base.UISupport
import com.clipevery.ui.resource.ClipResourceLoader
import com.clipevery.ui.resource.DesktopAbsoluteClipResourceLoader
import com.clipevery.ui.search.ClipeverySearchWindow
import com.clipevery.utils.IDGenerator
import com.clipevery.utils.IDGeneratorFactory
import com.clipevery.utils.QRCodeGenerator
import com.clipevery.utils.TelnetUtils
import com.clipevery.utils.getResourceUtils
import com.clipevery.utils.ioDispatcher
import dorkbox.systemTray.MenuItem
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
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import kotlin.io.path.pathString
import kotlin.system.exitProcess

class Clipevery {

    companion object {

        private val appEnv = AppEnv.getAppEnv()

        private val clipLogger =
            initLogger(
                DesktopPathProvider.resolve("clipevery.log", AppFileType.LOG).pathString,
            )

        private val logger: KLogger = KotlinLogging.logger {}

        val koinApplication: KoinApplication = initKoinApplication()

        private fun initKoinApplication(): KoinApplication {
            val appModule =
                module {
                    // simple component
                    single<AppEnv> { appEnv }
                    single<AppInfo> { DesktopAppInfoFactory(get()).createAppInfo() }
                    single<AppLock> { DesktopAppLock }
                    single<AppStartUpService> { DesktopAppStartUpService(get()) }
                    single<AppRestartService> { DesktopAppRestartService }
                    single<EndpointInfoFactory> { DesktopEndpointInfoFactory(lazy { get<ClipServer>() }) }
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
                    single<ClipeveryLogger> { clipLogger }
                    single<KLogger> { Clipevery.logger }

                    // realm component
                    single<RealmManager> { RealmManagerImpl.createRealmManager(get()) }
                    single<SignalDao> { SignalRealm(get<RealmManager>().realm) }
                    single<SyncRuntimeInfoDao> { SyncRuntimeInfoRealm(get<RealmManager>().realm) }
                    single<ClipDao> { ClipRealm(get<RealmManager>().realm, get(), lazy { get() }) }
                    single<ClipTaskDao> { ClipTaskRealm(get<RealmManager>().realm) }

                    // net component
                    single<ClipClient> { DesktopClipClient(get<AppInfo>()) }
                    single<ClipServer> { DesktopClipServer(get<ConfigManager>()) }
                    single<ClipBonjourService> { DesktopClipBonjourService(get(), get(), get()) }
                    single<TelnetUtils> { TelnetUtils(get<ClipClient>()) }
                    single<SyncClientApi> { DesktopSyncClientApi(get(), get()) }
                    single<SendClipClientApi> { DesktopSendClipClientApi(get(), get()) }
                    single<PullClientApi> { DesktopPullClientApi(get(), get()) }
                    single { DesktopSyncManager(get(), get(), get(), get(), get(), get(), lazy { get() }) }
                    single<SyncRefresher> { get<DesktopSyncManager>() }
                    single<SyncManager> { get<DesktopSyncManager>() }
                    single<DeviceManager> { DesktopDeviceManager(get(), get(), get()) }
                    single<FaviconLoader> { DesktopFaviconLoader }

                    // signal component
                    single<IdentityKeyStore> { getClipIdentityKeyStoreFactory(get(), get()).createIdentityKeyStore() }
                    single<SessionStore> { DesktopSessionStore(get()) }
                    single<PreKeyStore> { DesktopPreKeyStore(get()) }
                    single<SignedPreKeyStore> { DesktopSignedPreKeyStore(get()) }
                    single<SignalProtocolStore> { DesktopSignalProtocolStore(get(), get(), get(), get()) }

                    // clip component
                    single<ClipboardService> { getDesktopClipboardService(get(), get(), get(), get(), get()) }
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
                    single<ClipPreviewService> { DesktopClipPreviewService(get()) }
                    single<ClipSyncProcessManager<ObjectId>> { DesktopClipSyncProcessManager() }
                    single<ClipSearchService> { DesktopClipSearchService(get(), get(), get()) }
                    single<CleanClipScheduler> { DesktopCleanClipScheduler(get(), get(), get()) }
                    single<TaskExecutor> {
                        DesktopTaskExecutor(
                            listOf(
                                SyncClipTaskExecutor(get(), get(), get()),
                                DeleteClipTaskExecutor(get()),
                                PullFileTaskExecutor(get(), get(), get(), get(), get()),
                                CleanClipTaskExecutor(get(), get()),
                                Html2ImageTaskExecutor(get(), get(), get()),
                                PullIconTaskExecutor(get(), get(), get(), get()),
                            ),
                            get(),
                        )
                    }

                    // ui component
                    single<AppWindowManager> { DesktopAppWindowManager(lazy { get() }) }
                    single<GlobalCopywriter> { GlobalCopywriterImpl(get()) }
                    single<ShortcutKeysListener> { DesktopShortcutKeysListener(get()) }
                    single<GlobalListener> { DesktopGlobalListener(get()) }
                    single<ThemeDetector> { DesktopThemeDetector(get()) }
                    single<ClipResourceLoader> { DesktopAbsoluteClipResourceLoader }
                    single<ToastManager> { DesktopToastManager() }
                    single<NotificationManager> { DesktopNotificationManager }
                    single<MessageManager> { DesktopMessageManager(get()) }
                    single<IconStyle> { DesktopIconStyle }
                    single<UISupport> { DesktopUISupport(get(), get()) }
                    single<ShortcutKeys> { DesktopShortcutKeys(get(), get()) }
                    single<ShortcutKeysLoader> { DesktopShortcutKeysLoader(get()) }
                    single<ShortcutKeysAction> { DesktopShortKeysAction(get(), get(), get(), get(), get(), get()) }
                    single<DialogService> { DesktopDialogService() }
                }
            return GlobalContext.startKoin {
                modules(appModule)
            }
        }

        @Throws(Exception::class)
        private fun initInject() {
            try {
                if (koinApplication.koin.get<AppLock>().acquireLock()) {
                    koinApplication.koin.get<ClipboardService>().start()
                    koinApplication.koin.get<QRCodeGenerator>()
                    koinApplication.koin.get<ClipServer>().start()
                    koinApplication.koin.get<ClipClient>()
                    // bonjour service should be registered after clip server started
                    // only server started, bonjour service can get the port
                    koinApplication.koin.get<ClipBonjourService>().registerService()
                    koinApplication.koin.get<CleanClipScheduler>().start()
                    koinApplication.koin.get<AppStartUpService>().followConfig()
                } else {
                    exitProcess(0)
                }
            } catch (throwable: Throwable) {
                logger.error(throwable) { "cant start clipevery" }
                exitProcess(0)
            }
        }

        private fun exitClipEveryApplication(exitApplication: () -> Unit) {
            koinApplication.koin.get<AppLock>().releaseLock()
            koinApplication.koin.get<ChromeService>().quit()
            koinApplication.koin.get<ClipboardService>().stop()
            koinApplication.koin.get<ClipBonjourService>().unregisterService()
            koinApplication.koin.get<ClipServer>().stop()
            koinApplication.koin.get<SyncManager>().notifyExit()
            koinApplication.koin.get<CleanClipScheduler>().stop()
            koinApplication.koin.get<GlobalListener>().stop()
            exitApplication()
        }

        @JvmStatic
        fun main(args: Array<String>) {
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                logger.error(throwable) { "Uncaught exception in thread: $thread" }
            }

            logger.info { "Starting Clipevery" }
            initInject()
            logger.info { "Clipevery started" }

            val appWindowManager = koinApplication.koin.get<AppWindowManager>()
            val platform = currentPlatform()

            val isMacos = platform.isMacos()
            val isWindows = platform.isWindows()
            val isLinux = platform.isLinux()

            val resourceUtils = getResourceUtils()

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
                        exitClipEveryApplication { exitApplication() }
                    }
                }

                val currentPageViewContext = remember { mutableStateOf(PageViewContext(PageViewType.CLIP_PREVIEW)) }

                CompositionLocalProvider(
                    LocalKoinApplication provides koinApplication,
                    LocalExitApplication provides exitApplication,
                    LocalPageViewContent provides currentPageViewContext,
                ) {
                    val windowState =
                        rememberWindowState(
                            placement = WindowPlacement.Floating,
                            position = WindowPosition.PlatformDefault,
                            size = appWindowManager.mainWindowDpSize,
                        )

                    if (isMacos) {
                        MacTray(windowState)
                    } else if (isWindows) {
                        WindowsTray(windowState)
                    } else if (isLinux) {
                        LinuxTrayWindowState.setWindowPosition(appWindowManager, windowState)
                    }

                    val windowIcon: Painter? =
                        if (platform.isMacos()) {
                            painterResource("icon/clipevery.mac.png")
                        } else if (platform.isWindows()) {
                            painterResource("icon/clipevery.win.png")
                        } else if (platform.isLinux()) {
                            painterResource("icon/clipevery.linux.png")
                        } else {
                            null
                        }

                    Window(
                        onCloseRequest = exitApplication,
                        visible = appWindowManager.showMainWindow,
                        state = windowState,
                        title = MAIN_WINDOW_TITLE,
                        icon = windowIcon,
                        alwaysOnTop = true,
                        undecorated = true,
                        transparent = true,
                        resizable = false,
                    ) {
                        DisposableEffect(Unit) {
                            if (platform.isLinux()) {
                                systemTray?.setImage(resourceUtils.resourceInputStream("icon/clipevery.tray.linux.png"))
                                systemTray?.setTooltip("Clipevery")
                                systemTray?.menu?.add(
                                    MenuItem("Open Clipevery") { appWindowManager.activeMainWindow() },
                                )

                                systemTray?.menu?.add(
                                    MenuItem("Quit Clipevery") {
                                        exitApplication()
                                    },
                                )
                            }

                            koinApplication.koin.get<GlobalListener>().start()

                            val windowListener =
                                object : WindowAdapter() {
                                    override fun windowGainedFocus(e: WindowEvent?) {
                                        appWindowManager.showMainWindow = true
                                    }

                                    override fun windowLostFocus(e: WindowEvent?) {
                                        if (!appWindowManager.showMainDialog) {
                                            appWindowManager.unActiveMainWindow()
                                        }
                                    }
                                }

                            window.addWindowFocusListener(windowListener)

                            onDispose {
                                window.removeWindowFocusListener(windowListener)
                            }
                        }
                        ClipeveryWindow { appWindowManager.unActiveMainWindow() }
                    }

                    val searchWindowState =
                        rememberWindowState(
                            placement = WindowPlacement.Floating,
                            position = appWindowManager.searchWindowPosition,
                            size = appWindowManager.searchWindowDpSize,
                        )

                    Window(
                        onCloseRequest = ::exitApplication,
                        visible = appWindowManager.showSearchWindow,
                        state = searchWindowState,
                        title = SEARCH_WINDOW_TITLE,
                        alwaysOnTop = true,
                        undecorated = true,
                        transparent = true,
                        resizable = false,
                    ) {
                        DisposableEffect(Unit) {
                            val windowListener =
                                object : WindowAdapter() {
                                    override fun windowGainedFocus(e: WindowEvent?) {
                                        appWindowManager.showSearchWindow = true
                                    }

                                    override fun windowLostFocus(e: WindowEvent?) {
                                        appWindowManager.showSearchWindow = false
                                    }
                                }

                            window.addWindowFocusListener(windowListener)

                            onDispose {
                                window.removeWindowFocusListener(windowListener)
                            }
                        }

                        ClipeverySearchWindow()
                    }
                }
            }
        }
    }
}
