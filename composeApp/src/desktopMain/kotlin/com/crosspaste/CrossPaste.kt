package com.crosspaste

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.crosspaste.app.AbstractAppWindowManager.Companion.MAIN_WINDOW_TITLE
import com.crosspaste.app.AbstractAppWindowManager.Companion.SEARCH_WINDOW_TITLE
import com.crosspaste.app.AppEnv
import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppInfo
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
import com.crosspaste.clean.CleanClipScheduler
import com.crosspaste.clean.DesktopCleanClipScheduler
import com.crosspaste.clip.CacheManager
import com.crosspaste.clip.CacheManagerImpl
import com.crosspaste.clip.ChromeService
import com.crosspaste.clip.ClipPreviewService
import com.crosspaste.clip.ClipSearchService
import com.crosspaste.clip.ClipSyncProcessManager
import com.crosspaste.clip.ClipboardService
import com.crosspaste.clip.DesktopChromeService
import com.crosspaste.clip.DesktopClipPreviewService
import com.crosspaste.clip.DesktopClipSearchService
import com.crosspaste.clip.DesktopClipSyncProcessManager
import com.crosspaste.clip.DesktopTransferableConsumer
import com.crosspaste.clip.DesktopTransferableProducer
import com.crosspaste.clip.TransferableConsumer
import com.crosspaste.clip.TransferableProducer
import com.crosspaste.clip.getDesktopClipboardService
import com.crosspaste.clip.plugin.DistinctPlugin
import com.crosspaste.clip.plugin.FilesToImagesPlugin
import com.crosspaste.clip.plugin.GenerateUrlPlugin
import com.crosspaste.clip.plugin.RemoveFolderImagePlugin
import com.crosspaste.clip.plugin.SortPlugin
import com.crosspaste.clip.service.FilesItemService
import com.crosspaste.clip.service.HtmlItemService
import com.crosspaste.clip.service.ImageItemService
import com.crosspaste.clip.service.TextItemService
import com.crosspaste.clip.service.UrlItemService
import com.crosspaste.config.ConfigManager
import com.crosspaste.config.DefaultConfigManager
import com.crosspaste.dao.clip.ClipDao
import com.crosspaste.dao.clip.ClipRealm
import com.crosspaste.dao.signal.SignalDao
import com.crosspaste.dao.signal.SignalRealm
import com.crosspaste.dao.sync.SyncRuntimeInfoDao
import com.crosspaste.dao.sync.SyncRuntimeInfoRealm
import com.crosspaste.dao.task.ClipTaskDao
import com.crosspaste.dao.task.ClipTaskRealm
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
import com.crosspaste.net.ClipBonjourService
import com.crosspaste.net.ClipClient
import com.crosspaste.net.ClipServer
import com.crosspaste.net.DesktopClipBonjourService
import com.crosspaste.net.DesktopClipClient
import com.crosspaste.net.DesktopClipServer
import com.crosspaste.net.DesktopFaviconLoader
import com.crosspaste.net.DesktopSyncInfoFactory
import com.crosspaste.net.FaviconLoader
import com.crosspaste.net.SyncInfoFactory
import com.crosspaste.net.SyncRefresher
import com.crosspaste.net.clientapi.DesktopPullClientApi
import com.crosspaste.net.clientapi.DesktopSendClipClientApi
import com.crosspaste.net.clientapi.DesktopSyncClientApi
import com.crosspaste.net.clientapi.PullClientApi
import com.crosspaste.net.clientapi.SendClipClientApi
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.net.plugin.SignalClientDecryptPlugin
import com.crosspaste.net.plugin.SignalClientEncryptPlugin
import com.crosspaste.os.macos.api.MacosApi
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
import com.crosspaste.signal.getClipIdentityKeyStoreFactory
import com.crosspaste.sync.DesktopDeviceManager
import com.crosspaste.sync.DesktopQRCodeGenerator
import com.crosspaste.sync.DesktopSyncManager
import com.crosspaste.sync.DeviceManager
import com.crosspaste.sync.SyncManager
import com.crosspaste.task.CleanClipTaskExecutor
import com.crosspaste.task.DeleteClipTaskExecutor
import com.crosspaste.task.DesktopTaskExecutor
import com.crosspaste.task.Html2ImageTaskExecutor
import com.crosspaste.task.PullFileTaskExecutor
import com.crosspaste.task.PullIconTaskExecutor
import com.crosspaste.task.SyncClipTaskExecutor
import com.crosspaste.task.TaskExecutor
import com.crosspaste.ui.DesktopThemeDetector
import com.crosspaste.ui.LinuxTrayView.initSystemTray
import com.crosspaste.ui.LinuxTrayView.setWindowPosition
import com.crosspaste.ui.MacTray
import com.crosspaste.ui.PageViewContext
import com.crosspaste.ui.PageViewType
import com.crosspaste.ui.ThemeDetector
import com.crosspaste.ui.WindowsTray
import com.crosspaste.ui.base.CrossPasteGrantAccessibilityPermissions
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
import com.crosspaste.ui.resource.ClipResourceLoader
import com.crosspaste.ui.resource.DesktopAbsoluteClipResourceLoader
import com.crosspaste.ui.search.CrossPasteSearchWindow
import com.crosspaste.utils.GlobalCoroutineScope
import com.crosspaste.utils.GlobalCoroutineScopeImpl
import com.crosspaste.utils.GlobalCoroutineScopeImpl.mainCoroutineDispatcher
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
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import kotlin.io.path.pathString
import kotlin.system.exitProcess

class CrossPaste {

    companion object {

        private val appEnv = AppEnv.CURRENT

        private val clipLogger =
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
                    single<AppInfo> { DesktopAppInfoFactory(get()).createAppInfo() }
                    single<AppLock> { DesktopAppLaunch }
                    single<AppLaunchState> { DesktopAppLaunch.launch() }
                    single<AppStartUpService> { DesktopAppStartUpService(get()) }
                    single<AppRestartService> { DesktopAppRestartService }
                    single<EndpointInfoFactory> { DesktopEndpointInfoFactory(lazy { get<ClipServer>() }) }
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
                    single<CrossPasteLogger> { clipLogger }
                    single<KLogger> { CrossPaste.logger }

                    // realm component
                    single<RealmManager> { RealmManagerImpl.createRealmManager(get()) }
                    single<SignalDao> { SignalRealm(get<RealmManager>().realm) }
                    single<SyncRuntimeInfoDao> { SyncRuntimeInfoRealm(get<RealmManager>().realm) }
                    single<ClipDao> { ClipRealm(get<RealmManager>().realm, get(), lazy { get() }) }
                    single<ClipTaskDao> { ClipTaskRealm(get<RealmManager>().realm) }

                    // net component
                    single<ClipClient> { DesktopClipClient(get<AppInfo>(), get(), get()) }
                    single<ClipServer> { DesktopClipServer(get<ConfigManager>()) }
                    single<ClipBonjourService> { DesktopClipBonjourService(get(), get(), get()) }
                    single<TelnetUtils> { TelnetUtils(get<ClipClient>()) }
                    single<SyncClientApi> { DesktopSyncClientApi(get(), get()) }
                    single<SendClipClientApi> { DesktopSendClipClientApi(get(), get()) }
                    single<PullClientApi> { DesktopPullClientApi(get(), get()) }
                    single { DesktopSyncManager(get(), get(), get(), get(), get(), get(), get(), lazy { get() }) }
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
                    single<SignalProcessorCache> { SignalProcessorCacheImpl(get()) }
                    single<SignalClientEncryptPlugin> { SignalClientEncryptPlugin(get()) }
                    single<SignalClientDecryptPlugin> { SignalClientDecryptPlugin(get()) }

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
                    single<ClipResourceLoader> { DesktopAbsoluteClipResourceLoader }
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
                    if (koinApplication.koin.get<ConfigManager>().config.enableClipboardListening) {
                        koinApplication.koin.get<ClipboardService>().start()
                    }
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
                logger.error(throwable) { "cant start crosspaste" }
                exitProcess(0)
            }
        }

        private fun exitCrossPasteApplication(exitApplication: () -> Unit) {
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

            logger.info { "Starting CrossPaste" }
            initInject()
            logger.info { "CrossPaste started" }

            val appLaunchState = koinApplication.koin.get<AppLaunchState>()
            val appWindowManager = koinApplication.koin.get<AppWindowManager>()
            val globalListener = koinApplication.koin.get<GlobalListener>()
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

                val currentPageViewContext = remember { mutableStateOf(PageViewContext(PageViewType.CLIP_PREVIEW)) }

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

                        Window(
                            onCloseRequest = exitApplication,
                            visible = appWindowManager.showMainWindow,
                            state = appWindowManager.mainWindowState,
                            title = MAIN_WINDOW_TITLE,
                            icon = windowIcon,
                            alwaysOnTop = true,
                            undecorated = true,
                            transparent = true,
                            resizable = false,
                        ) {
                            DisposableEffect(Unit) {
                                if (platform.isLinux()) {
                                    systemTray?.let { tray ->
                                        initSystemTray(tray, koinApplication, exitApplication)
                                    }
                                }

                                globalListener.start()

                                val windowListener =
                                    object : WindowAdapter() {
                                        override fun windowGainedFocus(e: WindowEvent?) {
                                            appWindowManager.showMainWindow = true
                                        }

                                        override fun windowLostFocus(e: WindowEvent?) {
                                            mainCoroutineDispatcher.launch(CoroutineName("Hide CrossPaste")) {
                                                if (!appWindowManager.showMainDialog) {
                                                    appWindowManager.unActiveMainWindow()
                                                }
                                            }
                                        }
                                    }

                                window.addWindowFocusListener(windowListener)

                                onDispose {
                                    window.removeWindowFocusListener(windowListener)
                                }
                            }
                            CrossPasteWindow { appWindowManager.unActiveMainWindow() }
                        }

                        Window(
                            onCloseRequest = ::exitApplication,
                            visible = appWindowManager.showSearchWindow,
                            state = appWindowManager.searchWindowState,
                            title = SEARCH_WINDOW_TITLE,
                            icon = windowIcon,
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

                            CrossPasteSearchWindow()
                        }
                    } else {
                        val windowState =
                            rememberWindowState(
                                placement = WindowPlacement.Floating,
                                position = WindowPosition.PlatformDefault,
                                size = DpSize(width = 360.dp, height = 200.dp),
                            )

                        Window(
                            onCloseRequest = ::exitApplication,
                            visible = true,
                            state = windowState,
                            title = "Apply Accessibility Permissions",
                            icon = windowIcon,
                            alwaysOnTop = true,
                            undecorated = false,
                            resizable = false,
                        ) {
                            DisposableEffect(Unit) {
                                window.rootPane.apply {
                                    rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                                    rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                                    rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
                                }

                                onDispose {}
                            }

                            CrossPasteGrantAccessibilityPermissions {
                                MacosApi.INSTANCE.checkAccessibilityPermissions()
                            }
                        }
                    }
                }
            }
        }
    }
}
