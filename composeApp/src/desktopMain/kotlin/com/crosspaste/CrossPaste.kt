package com.crosspaste

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.application
import com.crosspaste.app.AppEnv
import com.crosspaste.app.AppExitService
import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppInfoFactory
import com.crosspaste.app.AppLaunchState
import com.crosspaste.app.AppLock
import com.crosspaste.app.AppRestartService
import com.crosspaste.app.AppStartUpService
import com.crosspaste.app.AppTokenService
import com.crosspaste.app.AppUpdateService
import com.crosspaste.app.AppUrls
import com.crosspaste.app.AppWindowManager
import com.crosspaste.app.DesktopAppExitService
import com.crosspaste.app.DesktopAppInfoFactory
import com.crosspaste.app.DesktopAppLaunch
import com.crosspaste.app.DesktopAppRestartService
import com.crosspaste.app.DesktopAppStartUpService
import com.crosspaste.app.DesktopAppTokenService
import com.crosspaste.app.DesktopAppUpdateService
import com.crosspaste.app.DesktopAppUrls
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.DesktopEndpointInfoFactory
import com.crosspaste.app.EndpointInfoFactory
import com.crosspaste.app.ExitMode
import com.crosspaste.app.VersionCompatibilityChecker
import com.crosspaste.app.getDesktopAppWindowManager
import com.crosspaste.clean.CleanPasteScheduler
import com.crosspaste.config.ConfigManager
import com.crosspaste.config.DefaultConfigManager
import com.crosspaste.html.ChromeService
import com.crosspaste.html.DesktopChromeService
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.i18n.GlobalCopywriterImpl
import com.crosspaste.image.DesktopFaviconLoader
import com.crosspaste.image.DesktopFileExtLoader
import com.crosspaste.image.DesktopImageDataLoader
import com.crosspaste.image.DesktopImageWriter
import com.crosspaste.image.DesktopThumbnailLoader
import com.crosspaste.image.FaviconLoader
import com.crosspaste.image.FileExtImageLoader
import com.crosspaste.image.ImageDataLoader
import com.crosspaste.image.ImageWriter
import com.crosspaste.image.ThumbnailLoader
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
import com.crosspaste.net.DesktopPasteBonjourService
import com.crosspaste.net.DesktopServerFactory
import com.crosspaste.net.DesktopServerModule
import com.crosspaste.net.PasteBonjourService
import com.crosspaste.net.PasteClient
import com.crosspaste.net.PasteServer
import com.crosspaste.net.ServerFactory
import com.crosspaste.net.ServerModule
import com.crosspaste.net.SyncInfoFactory
import com.crosspaste.net.SyncRefresher
import com.crosspaste.net.TelnetHelper
import com.crosspaste.net.clientapi.PullClientApi
import com.crosspaste.net.clientapi.SendPasteClientApi
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.net.exception.DesktopExceptionHandler
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.net.plugin.SignalClientDecryptPlugin
import com.crosspaste.net.plugin.SignalClientEncryptPlugin
import com.crosspaste.net.plugin.SignalServerDecryptionPluginFactory
import com.crosspaste.net.plugin.SignalServerEncryptPluginFactory
import com.crosspaste.paste.CacheManager
import com.crosspaste.paste.CacheManagerImpl
import com.crosspaste.paste.CurrentPaste
import com.crosspaste.paste.DefaultPasteSyncProcessManager
import com.crosspaste.paste.DesktopCurrentPaste
import com.crosspaste.paste.DesktopPasteIDGeneratorFactory
import com.crosspaste.paste.DesktopPastePreviewService
import com.crosspaste.paste.DesktopPasteSearchService
import com.crosspaste.paste.DesktopTransferableConsumer
import com.crosspaste.paste.DesktopTransferableProducer
import com.crosspaste.paste.PasteIDGenerator
import com.crosspaste.paste.PastePreviewService
import com.crosspaste.paste.PasteSearchService
import com.crosspaste.paste.PasteSyncProcessManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.TransferableConsumer
import com.crosspaste.paste.TransferableProducer
import com.crosspaste.paste.getDesktopPasteboardService
import com.crosspaste.paste.plugin.processs.DistinctPlugin
import com.crosspaste.paste.plugin.processs.FilesToImagesPlugin
import com.crosspaste.paste.plugin.processs.GenerateUrlPlugin
import com.crosspaste.paste.plugin.processs.RemoveFolderImagePlugin
import com.crosspaste.paste.plugin.processs.SortPlugin
import com.crosspaste.paste.plugin.type.FilesTypePlugin
import com.crosspaste.paste.plugin.type.HtmlTypePlugin
import com.crosspaste.paste.plugin.type.ImageTypePlugin
import com.crosspaste.paste.plugin.type.TextTypePlugin
import com.crosspaste.paste.plugin.type.TextUpdater
import com.crosspaste.paste.plugin.type.UrlTypePlugin
import com.crosspaste.path.AppPathProvider
import com.crosspaste.path.DesktopAppPathProvider
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.path.getPlatformPathProvider
import com.crosspaste.platform.getPlatform
import com.crosspaste.presist.FilePersist
import com.crosspaste.realm.RealmManager
import com.crosspaste.realm.paste.PasteRealm
import com.crosspaste.realm.signal.SignalRealm
import com.crosspaste.realm.sync.SyncRuntimeInfoRealm
import com.crosspaste.realm.task.PasteTaskRealm
import com.crosspaste.serializer.PreKeyBundleSerializer
import com.crosspaste.signal.DesktopPreKeySignalMessageFactory
import com.crosspaste.signal.DesktopPreKeyStore
import com.crosspaste.signal.DesktopSessionBuilderFactory
import com.crosspaste.signal.DesktopSessionStore
import com.crosspaste.signal.DesktopSignalProtocolStore
import com.crosspaste.signal.DesktopSignedPreKeyStore
import com.crosspaste.signal.PreKeyBundleCodecs
import com.crosspaste.signal.PreKeySignalMessageFactory
import com.crosspaste.signal.SessionBuilderFactory
import com.crosspaste.signal.SignalProcessorCache
import com.crosspaste.signal.SignalProcessorCacheImpl
import com.crosspaste.signal.SignalProtocolStoreInterface
import com.crosspaste.signal.getPasteIdentityKeyStoreFactory
import com.crosspaste.sound.DesktopSoundService
import com.crosspaste.sound.SoundService
import com.crosspaste.sync.DesktopQRCodeGenerator
import com.crosspaste.sync.DeviceListener
import com.crosspaste.sync.DeviceManager
import com.crosspaste.sync.QRCodeGenerator
import com.crosspaste.sync.SyncManager
import com.crosspaste.task.CleanPasteTaskExecutor
import com.crosspaste.task.DeletePasteTaskExecutor
import com.crosspaste.task.Html2ImageTaskExecutor
import com.crosspaste.task.PullFileTaskExecutor
import com.crosspaste.task.PullIconTaskExecutor
import com.crosspaste.task.SyncPasteTaskExecutor
import com.crosspaste.task.TaskExecutor
import com.crosspaste.ui.CrossPasteMainWindow
import com.crosspaste.ui.CrossPasteSearchWindow
import com.crosspaste.ui.DesktopThemeDetector
import com.crosspaste.ui.GrantAccessibilityPermissionsWindow
import com.crosspaste.ui.LinuxTrayView
import com.crosspaste.ui.LocalExitApplication
import com.crosspaste.ui.LocalPageViewContent
import com.crosspaste.ui.MacTrayView
import com.crosspaste.ui.ScreenContext
import com.crosspaste.ui.ScreenType
import com.crosspaste.ui.ThemeDetector
import com.crosspaste.ui.WindowsTrayView
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
import com.crosspaste.utils.GlobalCoroutineScope
import com.crosspaste.utils.GlobalCoroutineScopeImpl
import com.crosspaste.utils.ioDispatcher
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import com.github.kwhat.jnativehook.mouse.NativeMouseListener
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.realm.kotlin.Realm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.koin.core.KoinApplication
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.mongodb.kbson.ObjectId
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.PreKeyStore
import org.signal.libsignal.protocol.state.SessionStore
import org.signal.libsignal.protocol.state.SignedPreKeyStore
import java.awt.image.BufferedImage
import kotlin.system.exitProcess

class CrossPaste {

    companion object {

        private val appEnv = AppEnv.CURRENT

        private val appPathProvider = DesktopAppPathProvider

        private val configManager =
            DefaultConfigManager(
                FilePersist.createOneFilePersist(
                    appPathProvider.resolve("appConfig.json", AppFileType.USER),
                ),
            )

        private val crossPasteLogger =
            initLogger(
                appPathProvider.resolve("crosspaste.log", AppFileType.LOG).toString(),
            )

        private val logger: KLogger = KotlinLogging.logger {}

        val koinApplication: KoinApplication = initKoinApplication()

        private fun initKoinApplication(): KoinApplication {
            val appModule =
                module {
                    // simple component
                    single<AppEnv> { appEnv }
                    single<AppExitService> { DesktopAppExitService }
                    single<AppInfo> { get<AppInfoFactory>().createAppInfo() }
                    single<AppInfoFactory> { DesktopAppInfoFactory(get()) }
                    single<AppLaunchState> { DesktopAppLaunch.launch() }
                    single<AppLock> { DesktopAppLaunch }
                    single<AppPathProvider> { appPathProvider }
                    single<AppRestartService> { DesktopAppRestartService }
                    single<AppStartUpService> { DesktopAppStartUpService(get(), get()) }
                    single<AppUpdateService> { DesktopAppUpdateService(get(), get(), get(), get(), get()) }
                    single<AppUrls> { DesktopAppUrls }
                    single<CacheManager> { CacheManagerImpl(get(), get()) }
                    single<ConfigManager> { configManager }
                    single<CrossPasteLogger> { crossPasteLogger }
                    single<EndpointInfoFactory> { DesktopEndpointInfoFactory(lazy { get<PasteServer<*, *>>() }) }
                    single<FileExtImageLoader> { DesktopFileExtLoader(get(), get()) }
                    single<FilePersist> { FilePersist }
                    single<ImageWriter<BufferedImage>> { DesktopImageWriter }
                    single<GlobalCoroutineScope> { GlobalCoroutineScopeImpl }
                    single<KLogger> { CrossPaste.logger }
                    single<PasteIDGenerator> { DesktopPasteIDGeneratorFactory(get()).createIDGenerator() }
                    single<QRCodeGenerator> { DesktopQRCodeGenerator(get(), get()) }
                    single<SyncInfoFactory> { SyncInfoFactory(get(), get()) }
                    single<ThumbnailLoader> { DesktopThumbnailLoader(get()) }
                    single<UserDataPathProvider> { UserDataPathProvider(get(), getPlatformPathProvider()) }
                    single<VersionCompatibilityChecker> {
                        get<AppInfoFactory>().createVersionCompatibilityChecker()
                    }

                    // realm component
                    single<PasteRealm> { PasteRealm(get(), get(), get(), get(), lazy { get() }) }
                    single<PasteTaskRealm> { PasteTaskRealm(get()) }
                    single<Realm> { get<RealmManager>().realm }
                    single<RealmManager> { RealmManager.createRealmManager(get()) }
                    single<SignalRealm> { SignalRealm(get()) }
                    single<SyncRuntimeInfoRealm> { SyncRuntimeInfoRealm(get()) }

                    // net component
                    single<DeviceListener> { get<DeviceManager>() }
                    single<DeviceManager> { DeviceManager(get(), get(), get(), get()) }
                    single<ExceptionHandler> { DesktopExceptionHandler() }
                    single<FaviconLoader> { DesktopFaviconLoader(get()) }
                    single<PasteBonjourService> { DesktopPasteBonjourService(get(), get(), get()) }
                    single<PasteClient> { PasteClient(get<AppInfo>(), get(), get()) }
                    single<PasteServer<*, *>> {
                        PasteServer(
                            get(),
                            get<ServerFactory<NettyApplicationEngine, NettyApplicationEngine.Configuration>>(),
                            get(),
                        )
                    }
                    single<PullClientApi> { PullClientApi(get(), get()) }
                    single<SendPasteClientApi> { SendPasteClientApi(get(), get()) }
                    single<ServerFactory<NettyApplicationEngine, NettyApplicationEngine.Configuration>> {
                        DesktopServerFactory()
                    }
                    single<ServerModule> {
                        DesktopServerModule(
                            get(), get(), get(), get(), get(), get(), get(), get(),
                            get(), get(), get(), get(), get(), get(), get(),
                        )
                    }
                    single<SyncClientApi> { SyncClientApi(get(), get(), get()) }
                    single<SyncManager> {
                        SyncManager(
                            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
                            lazy { get() },
                        )
                    }
                    single<SyncRefresher> { get<SyncManager>() }
                    single<TelnetHelper> { TelnetHelper(get<PasteClient>()) }

                    // signal component
                    single<IdentityKeyStore> { getPasteIdentityKeyStoreFactory(get(), get()).createIdentityKeyStore() }
                    single<PreKeyBundleCodecs> { PreKeyBundleSerializer }
                    single<PreKeySignalMessageFactory> { DesktopPreKeySignalMessageFactory() }
                    single<PreKeyStore> { DesktopPreKeyStore(get()) }
                    single<SessionBuilderFactory> { DesktopSessionBuilderFactory(get()) }
                    single<SessionStore> { DesktopSessionStore(get()) }
                    single<SignalClientDecryptPlugin> { SignalClientDecryptPlugin(get()) }
                    single<SignalClientEncryptPlugin> { SignalClientEncryptPlugin(get()) }
                    single<SignalProcessorCache> { SignalProcessorCacheImpl(get()) }
                    single<SignalProtocolStoreInterface> { DesktopSignalProtocolStore(get(), get(), get(), get()) }
                    single<SignalServerDecryptionPluginFactory> { SignalServerDecryptionPluginFactory(get()) }
                    single<SignalServerEncryptPluginFactory> { SignalServerEncryptPluginFactory(get()) }
                    single<SignedPreKeyStore> { DesktopSignedPreKeyStore(get()) }

                    // paste component
                    single<ChromeService> { DesktopChromeService(get(), get()) }
                    single<CleanPasteScheduler> { CleanPasteScheduler(get(), get(), get()) }
                    single<CurrentPaste> { DesktopCurrentPaste(lazy { get() }) }
                    single<DesktopPasteSearchService> { DesktopPasteSearchService(get(), get(), get()) }
                    single<FilesTypePlugin> { FilesTypePlugin(get(), get(), get()) }
                    single<HtmlTypePlugin> { HtmlTypePlugin(get()) }
                    single<ImageDataLoader> { DesktopImageDataLoader(get()) }
                    single<ImageTypePlugin> { ImageTypePlugin(get(), get(), get()) }
                    single<PasteboardService> {
                        getDesktopPasteboardService(get(), get(), get(), get(), get(), get(), get())
                    }
                    single<PastePreviewService> { DesktopPastePreviewService(get()) }
                    single<PasteSearchService> { get<DesktopPasteSearchService>() }
                    single<PasteSyncProcessManager<ObjectId>> { DefaultPasteSyncProcessManager() }
                    single<TaskExecutor> {
                        TaskExecutor(
                            listOf(
                                SyncPasteTaskExecutor(get(), get(), get()),
                                DeletePasteTaskExecutor(get()),
                                PullFileTaskExecutor(get(), get(), get(), get(), get(), get(), get()),
                                CleanPasteTaskExecutor(get(), get()),
                                Html2ImageTaskExecutor(lazy { get() }, get(), get(), get()),
                                PullIconTaskExecutor(get(), get(), get(), get()),
                            ),
                            get(),
                        )
                    }
                    single<TextTypePlugin> { TextTypePlugin() }
                    single<TextUpdater> { get<TextTypePlugin>() }
                    single<TransferableConsumer> {
                        DesktopTransferableConsumer(
                            get(),
                            get(),
                            get(),
                            listOf(
                                DistinctPlugin(get()),
                                GenerateUrlPlugin,
                                FilesToImagesPlugin(get()),
                                RemoveFolderImagePlugin(get()),
                                SortPlugin,
                            ),
                            listOf(
                                get<FilesTypePlugin>(),
                                get<HtmlTypePlugin>(),
                                get<ImageTypePlugin>(),
                                get<TextTypePlugin>(),
                                get<UrlTypePlugin>(),
                            ),
                        )
                    }
                    single<TransferableProducer> {
                        DesktopTransferableProducer(
                            listOf(
                                get<FilesTypePlugin>(),
                                get<HtmlTypePlugin>(),
                                get<ImageTypePlugin>(),
                                get<TextTypePlugin>(),
                                get<UrlTypePlugin>(),
                            ),
                        )
                    }
                    single<UrlTypePlugin> { UrlTypePlugin() }

                    // ui component
                    single<ActiveGraphicsDevice> { get<DesktopMouseListener>() }
                    single<AppTokenService> { DesktopAppTokenService(get()) }
                    single<AppWindowManager> { get<DesktopAppWindowManager>() }
                    single<DesktopAppWindowManager> { getDesktopAppWindowManager(lazy { get() }, get(), get()) }
                    single<DesktopMouseListener> { DesktopMouseListener }
                    single<DesktopShortcutKeysListener> { DesktopShortcutKeysListener(get()) }
                    single<DialogService> { DesktopDialogService() }
                    single<GlobalCopywriter> { GlobalCopywriterImpl(get()) }
                    single<GlobalListener> { DesktopGlobalListener(get(), get(), get(), get(), get()) }
                    single<IconStyle> { DesktopIconStyle(get(), get()) }
                    single<NativeKeyListener> { get<DesktopShortcutKeysListener>() }
                    single<NativeMouseListener> { get<DesktopMouseListener>() }
                    single<NotificationManager> { DesktopNotificationManager(get(), get(), get()) }
                    single<ShortcutKeys> { DesktopShortcutKeys(get()) }
                    single<ShortcutKeysAction> { DesktopShortKeysAction(get(), get(), get(), get(), get(), get(), get()) }
                    single<ShortcutKeysListener> { get<DesktopShortcutKeysListener>() }
                    single<ShortcutKeysLoader> { DesktopShortcutKeysLoader(get()) }
                    single<SoundService> { DesktopSoundService }
                    single<ThemeDetector> { DesktopThemeDetector(get()) }
                    single<ToastManager> { DesktopToastManager() }
                    single<UISupport> { DesktopUISupport(get(), get(), get(), get()) }
                }
            return GlobalContext.startKoin {
                modules(appModule)
            }
        }

        @Throws(Exception::class)
        private fun initInject() {
            try {
                val koin = koinApplication.koin
                val appLaunchState = koin.get<AppLaunchState>()
                if (appLaunchState.acquireLock) {
                    if (koin.get<ConfigManager>().config.enablePasteboardListening) {
                        koin.get<PasteboardService>().start()
                    }
                    koin.get<QRCodeGenerator>()
                    koin.get<PasteServer<*, *>>().start()
                    koin.get<PasteClient>()
                    // bonjour service should be registered after paste server started
                    // only server started, bonjour service can get the port
                    koin.get<PasteBonjourService>().registerService()
                    koin.get<CleanPasteScheduler>().start()
                    koin.get<AppStartUpService>().followConfig()
                    koin.get<AppUpdateService>().start()
                } else {
                    exitProcess(0)
                }
            } catch (throwable: Throwable) {
                logger.error(throwable) { "cant start crosspaste" }
                exitProcess(0)
            }
        }

        private suspend fun exitCrossPasteApplication(
            exitMode: ExitMode,
            scope: CoroutineScope,
            exitApplication: () -> Unit,
        ) = withContext(scope.coroutineContext) {
            val koin = koinApplication.koin
            val appExitService = koin.get<AppExitService>()
            appExitService.beforeExitList.forEach {
                it.invoke()
            }

            shutdownAllServices()

            appExitService.beforeReleaseLockList.forEach { it.invoke() }
            logger.info { "beforeReleaseLockList execution completed" }
            koin.get<AppLock>().releaseLock()
            logger.info { "AppLock release completed" }
            if (exitMode == ExitMode.MIGRATION) {
                val appWindowManager = koin.get<DesktopAppWindowManager>()
                appWindowManager.showMainWindow = false
            }
            exitApplication()
        }

        private suspend fun shutdownAllServices() {
            supervisorScope {
                val jobs =
                    listOf(
                        async { stopService<AppUpdateService>("AppUpdateService") { it.stop() } },
                        async { stopService<ChromeService>("ChromeService") { it.quit() } },
                        async { stopService<PasteboardService>("PasteboardService") { it.stop() } },
                        async { stopService<PasteBonjourService>("PasteBonjourService") { it.unregisterService() } },
                        async { stopService<PasteServer<*, *>>("PasteServer") { it.stop() } },
                        async { stopService<SyncManager>("SyncManager") { it.notifyExit() } },
                        async { stopService<CleanPasteScheduler>("CleanPasteScheduler") { it.stop() } },
                        async { stopService<GlobalListener>("GlobalListener") { it.stop() } },
                        async { stopService<UserDataPathProvider>("UserDataPathProvider") { it.cleanTemp() } },
                    )

                jobs.awaitAll()
            }
        }

        private inline fun <reified T : Any> stopService(
            serviceName: String,
            stopAction: (T) -> Unit,
        ) {
            try {
                val service = koinApplication.koin.get<T>()
                stopAction(service)
                logger.info { "$serviceName stop completed" }
            } catch (e: Exception) {
                logger.error(e) { "Error stopping $serviceName" }
            }
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
            val appWindowManager = koinApplication.koin.get<DesktopAppWindowManager>()
            val platform = getPlatform()

            val isMacos = platform.isMacos()
            val isWindows = platform.isWindows()
            val isLinux = platform.isLinux()

            application {
                val ioScope = rememberCoroutineScope { ioDispatcher }

                var exiting by remember { mutableStateOf(false) }

                val exitApplication: (ExitMode) -> Unit = { mode ->
                    if (mode == ExitMode.EXIT || mode == ExitMode.RESTART) {
                        appWindowManager.showMainWindow = false
                    }
                    exiting = true
                    appWindowManager.showSearchWindow = false
                    ioScope.launch {
                        exitCrossPasteApplication(mode, ioScope) { exitApplication() }
                    }
                }

                val currentScreenContext = remember { mutableStateOf(ScreenContext(ScreenType.PASTE_PREVIEW)) }

                CompositionLocalProvider(
                    LocalExitApplication provides exitApplication,
                    LocalPageViewContent provides currentScreenContext,
                ) {
                    val windowIcon: Painter? =
                        if (platform.isMacos()) {
                            painterResource("icon/crosspaste.mac.png")
                        } else if (platform.isWindows() || platform.isLinux()) {
                            painterResource("icon/crosspaste.png")
                        } else {
                            null
                        }

                    if (appLaunchState.accessibilityPermissions) {
                        if (!exiting) {
                            if (isMacos) {
                                MacTrayView.Tray()
                            } else if (isWindows) {
                                WindowsTrayView.Tray()
                            } else if (isLinux) {
                                LinuxTrayView.Tray()
                            }
                        }

                        CrossPasteMainWindow(exitApplication, windowIcon)

                        CrossPasteSearchWindow(windowIcon)
                    } else {
                        GrantAccessibilityPermissionsWindow(windowIcon)
                    }
                }
            }
        }
    }
}
