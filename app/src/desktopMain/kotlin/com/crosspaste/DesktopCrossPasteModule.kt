package com.crosspaste

import coil3.PlatformContext
import com.crosspaste.app.AppControl
import com.crosspaste.app.AppEnv
import com.crosspaste.app.AppExitService
import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppInfoFactory
import com.crosspaste.app.AppLock
import com.crosspaste.app.AppRestartService
import com.crosspaste.app.AppSize
import com.crosspaste.app.AppStartUpService
import com.crosspaste.app.AppTokenApi
import com.crosspaste.app.AppUpdateService
import com.crosspaste.app.AppUrls
import com.crosspaste.app.AppWindowManager
import com.crosspaste.app.DesktopAppControl
import com.crosspaste.app.DesktopAppExitService
import com.crosspaste.app.DesktopAppInfoFactory
import com.crosspaste.app.DesktopAppLaunch
import com.crosspaste.app.DesktopAppLaunchState
import com.crosspaste.app.DesktopAppRestartService
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppStartUpService
import com.crosspaste.app.DesktopAppTokenService
import com.crosspaste.app.DesktopAppUpdateService
import com.crosspaste.app.DesktopAppUrls
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.EndpointInfoFactory
import com.crosspaste.app.getDesktopAppWindowManager
import com.crosspaste.clean.CleanScheduler
import com.crosspaste.config.ConfigManager
import com.crosspaste.config.DevConfig
import com.crosspaste.config.ReadWriteConfig
import com.crosspaste.config.ReadWritePort
import com.crosspaste.db.DesktopDriverFactory
import com.crosspaste.db.DriverFactory
import com.crosspaste.db.createDatabase
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.secure.SecureDao
import com.crosspaste.db.secure.SecureIO
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.db.task.TaskDao
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.i18n.GlobalCopywriterImpl
import com.crosspaste.image.DesktopFaviconLoader
import com.crosspaste.image.DesktopFileExtLoader
import com.crosspaste.image.DesktopImageWriter
import com.crosspaste.image.DesktopThumbnailLoader
import com.crosspaste.image.FaviconLoader
import com.crosspaste.image.FileExtImageLoader
import com.crosspaste.image.GenerateImageService
import com.crosspaste.image.ImageWriter
import com.crosspaste.image.ThumbnailLoader
import com.crosspaste.image.coil.ImageLoaders
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
import com.crosspaste.net.DesktopPasteBonjourService
import com.crosspaste.net.DesktopPasteServer
import com.crosspaste.net.DesktopServerFactory
import com.crosspaste.net.DesktopServerModule
import com.crosspaste.net.PasteBonjourService
import com.crosspaste.net.PasteClient
import com.crosspaste.net.Server
import com.crosspaste.net.ServerFactory
import com.crosspaste.net.ServerModule
import com.crosspaste.net.SyncApi
import com.crosspaste.net.SyncInfoFactory
import com.crosspaste.net.TelnetHelper
import com.crosspaste.net.clientapi.PasteClientApi
import com.crosspaste.net.clientapi.PullClientApi
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.net.exception.DesktopExceptionHandler
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.net.plugin.ClientDecryptPlugin
import com.crosspaste.net.plugin.ClientEncryptPlugin
import com.crosspaste.net.plugin.ServerDecryptionPluginFactory
import com.crosspaste.net.plugin.ServerEncryptPluginFactory
import com.crosspaste.net.routing.SyncRoutingApi
import com.crosspaste.notification.NotificationManager
import com.crosspaste.notification.ToastManager
import com.crosspaste.paste.CacheManager
import com.crosspaste.paste.CurrentPaste
import com.crosspaste.paste.DefaultPasteSyncProcessManager
import com.crosspaste.paste.DesktopCacheManager
import com.crosspaste.paste.DesktopCurrentPaste
import com.crosspaste.paste.DesktopPasteMenuService
import com.crosspaste.paste.DesktopTransferableConsumer
import com.crosspaste.paste.DesktopTransferableProducer
import com.crosspaste.paste.PasteExportService
import com.crosspaste.paste.PasteImportService
import com.crosspaste.paste.PasteSyncProcessManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.TransferableConsumer
import com.crosspaste.paste.TransferableProducer
import com.crosspaste.paste.getDesktopPasteboardService
import com.crosspaste.paste.plugin.process.DistinctPlugin
import com.crosspaste.paste.plugin.process.FilesToImagesPlugin
import com.crosspaste.paste.plugin.process.GenerateUrlPlugin
import com.crosspaste.paste.plugin.process.RemoveFolderImagePlugin
import com.crosspaste.paste.plugin.process.RemoveHtmlImagePlugin
import com.crosspaste.paste.plugin.process.RemoveInvalidPlugin
import com.crosspaste.paste.plugin.process.SortPlugin
import com.crosspaste.paste.plugin.process.TextToColorPlugin
import com.crosspaste.paste.plugin.type.ColorTypePlugin
import com.crosspaste.paste.plugin.type.DesktopColorTypePlugin
import com.crosspaste.paste.plugin.type.DesktopFilesTypePlugin
import com.crosspaste.paste.plugin.type.DesktopHtmlTypePlugin
import com.crosspaste.paste.plugin.type.DesktopImageTypePlugin
import com.crosspaste.paste.plugin.type.DesktopRtfTypePlugin
import com.crosspaste.paste.plugin.type.DesktopTextTypePlugin
import com.crosspaste.paste.plugin.type.DesktopUrlTypePlugin
import com.crosspaste.paste.plugin.type.FilesTypePlugin
import com.crosspaste.paste.plugin.type.HtmlTypePlugin
import com.crosspaste.paste.plugin.type.ImageTypePlugin
import com.crosspaste.paste.plugin.type.RtfTypePlugin
import com.crosspaste.paste.plugin.type.TextTypePlugin
import com.crosspaste.paste.plugin.type.UrlTypePlugin
import com.crosspaste.path.AppPathProvider
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.path.getPlatformPathProvider
import com.crosspaste.presist.FilePersist
import com.crosspaste.rendering.DesktopHtmlRenderingService
import com.crosspaste.rendering.DesktopRenderingHelper
import com.crosspaste.rendering.DesktopRtfRenderingService
import com.crosspaste.rendering.RenderingHelper
import com.crosspaste.rendering.RenderingService
import com.crosspaste.secure.DesktopSecureStoreFactory
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.secure.SecureStore
import com.crosspaste.secure.SecureStoreFactory
import com.crosspaste.sound.DesktopSoundService
import com.crosspaste.sound.SoundService
import com.crosspaste.sync.DesktopQRCodeGenerator
import com.crosspaste.sync.GeneralNearbyDeviceManager
import com.crosspaste.sync.GeneralSyncManager
import com.crosspaste.sync.MarketingNearbyDeviceManager
import com.crosspaste.sync.MarketingSyncManager
import com.crosspaste.sync.NearbyDeviceManager
import com.crosspaste.sync.QRCodeGenerator
import com.crosspaste.sync.SyncManager
import com.crosspaste.sync.TokenCache
import com.crosspaste.task.CleanPasteTaskExecutor
import com.crosspaste.task.CleanTaskTaskExecutor
import com.crosspaste.task.DeletePasteTaskExecutor
import com.crosspaste.task.Html2ImageTaskExecutor
import com.crosspaste.task.PullFileTaskExecutor
import com.crosspaste.task.PullIconTaskExecutor
import com.crosspaste.task.Rtf2ImageTaskExecutor
import com.crosspaste.task.SyncPasteTaskExecutor
import com.crosspaste.task.TaskExecutor
import com.crosspaste.ui.DesktopScreenProvider
import com.crosspaste.ui.DesktopThemeDetector
import com.crosspaste.ui.ScreenProvider
import com.crosspaste.ui.base.DesktopIconStyle
import com.crosspaste.ui.base.DesktopNotificationManager
import com.crosspaste.ui.base.DesktopUISupport
import com.crosspaste.ui.base.DialogService
import com.crosspaste.ui.base.IconStyle
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.devices.DesktopDeviceViewProvider
import com.crosspaste.ui.devices.DeviceViewProvider
import com.crosspaste.ui.model.GeneralPasteDataViewModel
import com.crosspaste.ui.model.MarketingPasteDataViewModel
import com.crosspaste.ui.model.PasteDataViewModel
import com.crosspaste.ui.model.PasteSearchViewModel
import com.crosspaste.ui.model.PasteSelectionViewModel
import com.crosspaste.ui.paste.DesktopPasteboardViewProvider
import com.crosspaste.ui.paste.PasteboardViewProvider
import com.crosspaste.ui.settings.DesktopSettingsViewProvider
import com.crosspaste.ui.settings.SettingsViewProvider
import com.crosspaste.ui.theme.ThemeDetector
import com.crosspaste.utils.DesktopDeviceUtils
import com.crosspaste.utils.DesktopLocaleUtils
import com.crosspaste.utils.DeviceUtils
import com.crosspaste.utils.LocaleUtils
import com.crosspaste.utils.getAppEnvUtils
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import com.github.kwhat.jnativehook.mouse.NativeMouseListener
import io.github.oshai.kotlinlogging.KLogger
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import org.koin.core.KoinApplication
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.awt.image.BufferedImage

class DesktopCrossPasteModule(
    private val appEnv: AppEnv,
    private val appPathProvider: AppPathProvider,
    private val configManager: ConfigManager,
    private val crossPasteLogger: CrossPasteLogger,
    private val klogger: KLogger,
) : CrossPasteModule {

    val marketingMode = getAppEnvUtils().isDevelopment() && DevConfig.marketingMode

    override fun appModule() =
        module {
            single<AppControl> { DesktopAppControl(configManager) }
            single<AppEnv> { appEnv }
            single<AppExitService> { DesktopAppExitService }
            single<AppInfo> { get<AppInfoFactory>().createAppInfo() }
            single<AppInfoFactory> { DesktopAppInfoFactory(get()) }
            single<AppLock> { DesktopAppLaunch }
            single<AppPathProvider> { appPathProvider }
            single<AppRestartService> { DesktopAppRestartService }
            single<AppStartUpService> { DesktopAppStartUpService(get(), get()) }
            single<AppUpdateService> { DesktopAppUpdateService(get(), get(), get(), get()) }
            single<AppUrls> { DesktopAppUrls }
            single<CacheManager> { DesktopCacheManager(get(), get()) }
            single<ConfigManager> { configManager }
            single<CrossPasteLogger> { crossPasteLogger }
            single<DesktopAppLaunchState> { runBlocking { DesktopAppLaunch.launch() } }
            single<DeviceUtils> { DesktopDeviceUtils }
            single<EndpointInfoFactory> { EndpointInfoFactory(get(), lazy { get<Server>() }) }
            single<FileExtImageLoader> { DesktopFileExtLoader(get(), get()) }
            single<FilePersist> { FilePersist }
            single<ImageLoaders> { ImageLoaders(get(), get(), get(), get(), get(), get(), get()) }
            single<ImageWriter<BufferedImage>> { DesktopImageWriter }
            single<KLogger> { klogger }
            single<LocaleUtils> { DesktopLocaleUtils }
            single<QRCodeGenerator> { DesktopQRCodeGenerator(get(), get()) }
            single<ReadWriteConfig<Int>>(named("readWritePort")) { ReadWritePort(get()) }
            single<SyncInfoFactory> { SyncInfoFactory(get(), get()) }
            single<ThumbnailLoader> { DesktopThumbnailLoader(get()) }
            single<UserDataPathProvider> { UserDataPathProvider(get(), getPlatformPathProvider()) }
        }

    // SqlDelight
    override fun sqlDelightModule() =
        module {
            single<DriverFactory> { DesktopDriverFactory(get()) }
            single<Database> { createDatabase(get()) }
            single<PasteDao> {
                PasteDao(
                    get(),
                    get(),
                    get(),
                    get(),
                    lazy { get() },
                    listOf(
                        RemoveInvalidPlugin,
                        DistinctPlugin(get()),
                        GenerateUrlPlugin,
                        TextToColorPlugin,
                        FilesToImagesPlugin(get()),
                        RemoveFolderImagePlugin(get()),
                        RemoveHtmlImagePlugin(get()),
                        SortPlugin,
                    ),
                    get(),
                    get(),
                )
            }
            single<SecureIO> { SecureDao(get()) }
            single<SyncRuntimeInfoDao> { SyncRuntimeInfoDao(get()) }
            single<TaskDao> { TaskDao(get()) }
        }

    // NetworkModule.kt
    override fun networkModule() =
        module {
            single<NearbyDeviceManager> {
                if (marketingMode) {
                    MarketingNearbyDeviceManager()
                } else {
                    GeneralNearbyDeviceManager(get(), get(), get(), get())
                }
            }
            single<ExceptionHandler> { DesktopExceptionHandler() }
            single<FaviconLoader> { DesktopFaviconLoader(get()) }
            single<PasteBonjourService> { DesktopPasteBonjourService(get(), get(), get()) }
            single<PasteClient> { PasteClient(get<AppInfo>(), get(), get()) }
            single<PullClientApi> { PullClientApi(get(), get()) }
            single<PasteClientApi> { PasteClientApi(get(), get()) }
            single<Server> {
                DesktopPasteServer(
                    get(named("readWritePort")),
                    get(),
                    get<ServerFactory<NettyApplicationEngine, NettyApplicationEngine.Configuration>>(),
                    get(),
                )
            }
            single<ServerFactory<NettyApplicationEngine, NettyApplicationEngine.Configuration>> {
                DesktopServerFactory()
            }
            single<ServerModule> {
                DesktopServerModule(
                    get(), get(), get(), get(), get(), get(), get(), get(),
                    get(), get(), get(), get(), get(), get(),
                )
            }
            single<SyncApi> { SyncApi }
            single<SyncClientApi> { SyncClientApi(get(), get(), get(), get(), get()) }
            single<SyncManager> {
                if (marketingMode) {
                    MarketingSyncManager()
                } else {
                    GeneralSyncManager(
                        get(),
                        get(),
                        get(),
                        get(),
                        get(),
                        get(),
                        get(),
                        lazy { get() },
                    )
                }
            }
            single<SyncRoutingApi> { get<SyncManager>() }
            single<TelnetHelper> { TelnetHelper(get(), get()) }
        }

    // SecurityModule.kt
    override fun securityModule() =
        module {
            single<ClientDecryptPlugin> { ClientDecryptPlugin(get()) }
            single<ClientEncryptPlugin> { ClientEncryptPlugin(get()) }
            single<SecureKeyPairSerializer> { SecureKeyPairSerializer() }
            single<SecureStore> { get<SecureStoreFactory>().createSecureStore() }
            single<SecureStoreFactory> { DesktopSecureStoreFactory(get(), get(), get()) }
            single<ServerDecryptionPluginFactory> { ServerDecryptionPluginFactory(get()) }
            single<ServerEncryptPluginFactory> { ServerEncryptPluginFactory(get()) }
        }

    // PasteTypePluginModule.kt
    override fun pasteTypePluginModule() =
        module {
            single<ColorTypePlugin> { DesktopColorTypePlugin() }
            single<FilesTypePlugin> { DesktopFilesTypePlugin(get(), get(), get()) }
            single<HtmlTypePlugin> { DesktopHtmlTypePlugin(get()) }
            single<ImageTypePlugin> { DesktopImageTypePlugin(get(), get(), get()) }
            single<RtfTypePlugin> { DesktopRtfTypePlugin(get()) }
            single<TextTypePlugin> { DesktopTextTypePlugin() }
            single<UrlTypePlugin> { DesktopUrlTypePlugin() }
        }

    // PasteComponentModule.kt
    override fun pasteComponentModule() =
        module {
            single<CleanScheduler> { CleanScheduler(get(), get(), get()) }
            single<CurrentPaste> { DesktopCurrentPaste(lazy { get() }) }
            single<RenderingHelper> { DesktopRenderingHelper(get()) }
            single<RenderingService<String>>(named("htmlRendering")) {
                DesktopHtmlRenderingService(get(), get(), get())
            }
            single<RenderingService<String>>(named("rtfRendering")) {
                DesktopRtfRenderingService(get(), get())
            }
            single<DesktopPasteMenuService> { DesktopPasteMenuService(get(), get(), get(), get(), get(), get()) }
            single<GenerateImageService> { GenerateImageService() }
            single<PasteboardService> {
                getDesktopPasteboardService(get(), get(), get(), get(), get(), get(), get(), get())
            }
            single<PasteExportService> { PasteExportService(get(), get(), get()) }
            single<PasteImportService> { PasteImportService(get(), get(), get()) }
            single<PasteSyncProcessManager<Long>> { DefaultPasteSyncProcessManager() }
            single<TaskExecutor> {
                TaskExecutor(
                    listOf(
                        CleanPasteTaskExecutor(get(), get()),
                        CleanTaskTaskExecutor(get()),
                        DeletePasteTaskExecutor(get()),
                        Html2ImageTaskExecutor(
                            lazy { get<RenderingService<String>>(named("htmlRendering")) },
                            get(),
                            get(),
                            get(),
                            get(),
                        ),
                        PullFileTaskExecutor(get(), get(), get(), get(), get(), get(), get()),
                        PullIconTaskExecutor(get(), get(), get(), get()),
                        Rtf2ImageTaskExecutor(
                            lazy { get<RenderingService<String>>(named("rtfRendering")) },
                            get(),
                            get(),
                            get(),
                        ),
                        SyncPasteTaskExecutor(get(), get(), get(), get()),
                    ),
                    get(),
                )
            }
            single<TransferableConsumer> {
                DesktopTransferableConsumer(
                    get(),
                    get(),
                    listOf(
                        get<ColorTypePlugin>(),
                        get<FilesTypePlugin>(),
                        get<HtmlTypePlugin>(),
                        get<RtfTypePlugin>(),
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
                        get<RtfTypePlugin>(),
                        get<ImageTypePlugin>(),
                        get<TextTypePlugin>(),
                        get<UrlTypePlugin>(),
                    ),
                )
            }
        }

    // UIModule.kt
    override fun uiModule() =
        module {
            single<ActiveGraphicsDevice> { get<DesktopMouseListener>() }
            single<AppSize> { DesktopAppSize }
            single<AppTokenApi> { DesktopAppTokenService(get()) }
            single<AppWindowManager> { get<DesktopAppWindowManager>() }
            single<DesktopAppSize> { DesktopAppSize }
            single<DesktopAppWindowManager> { getDesktopAppWindowManager(get(), lazy { get() }, get(), get()) }
            single<DesktopMouseListener> { DesktopMouseListener }
            single<DesktopShortcutKeysListener> { DesktopShortcutKeysListener(get()) }
            single<DeviceViewProvider> { DesktopDeviceViewProvider() }
            single<DialogService> { DialogService }
            single<GlobalCopywriter> { GlobalCopywriterImpl(get()) }
            single<GlobalListener> { DesktopGlobalListener(get(), get(), get(), get()) }
            single<IconStyle> { DesktopIconStyle(get()) }
            single<NativeKeyListener> { get<DesktopShortcutKeysListener>() }
            single<NativeMouseListener> { get<DesktopMouseListener>() }
            single<NotificationManager> { DesktopNotificationManager(get(), get(), get(), get()) }
            single<PasteboardViewProvider> { DesktopPasteboardViewProvider() }
            single<PlatformContext> { PlatformContext.INSTANCE }
            single<ScreenProvider> { DesktopScreenProvider(get()) }
            single<SettingsViewProvider> { DesktopSettingsViewProvider(get(), get()) }
            single<ShortcutKeys> { DesktopShortcutKeys(get()) }
            single<ShortcutKeysAction> { DesktopShortKeysAction(get(), get(), get(), get(), get(), get()) }
            single<ShortcutKeysListener> { get<DesktopShortcutKeysListener>() }
            single<ShortcutKeysLoader> { DesktopShortcutKeysLoader(get()) }
            single<SoundService> { DesktopSoundService(get()) }
            single<ThemeDetector> { DesktopThemeDetector(get()) }
            single<ToastManager> { ToastManager() }
            single<TokenCache> { TokenCache }
            single<UISupport> { DesktopUISupport(get(), get(), get(), get(), get(), get(), get()) }
        }

    // ViewModelModule.kt
    override fun viewModelModule() =
        module {
            single<PasteDataViewModel> {
                if (marketingMode) {
                    MarketingPasteDataViewModel(get(), get())
                } else {
                    GeneralPasteDataViewModel(get())
                }
            }
            single<PasteSearchViewModel> { PasteSearchViewModel(get()) }
            single<PasteSelectionViewModel> { PasteSelectionViewModel(get(), get(), get()) }
        }

    // Application.kt
    fun initKoinApplication(): KoinApplication {
        return GlobalContext.startKoin {
            modules(
                appModule(),
                sqlDelightModule(),
                networkModule(),
                securityModule(),
                pasteTypePluginModule(),
                pasteComponentModule(),
                uiModule(),
                viewModelModule(),
            )
        }
    }
}
