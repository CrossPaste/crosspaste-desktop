package com.crosspaste

import coil3.PlatformContext
import com.crosspaste.app.AppControl
import com.crosspaste.app.AppEnv
import com.crosspaste.app.AppExitService
import com.crosspaste.app.AppFileChooser
import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppInfoFactory
import com.crosspaste.app.AppLaunchState
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
import com.crosspaste.app.DesktopAppFileChooser
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
import com.crosspaste.app.DesktopRatingPromptManager
import com.crosspaste.app.EndpointInfoFactory
import com.crosspaste.app.RatingPromptManager
import com.crosspaste.app.getDesktopAppWindowManager
import com.crosspaste.clean.CleanScheduler
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.config.DesktopSimpleConfigFactory
import com.crosspaste.config.DevConfig
import com.crosspaste.config.ReadWriteConfig
import com.crosspaste.config.ReadWritePort
import com.crosspaste.config.SimpleConfigFactory
import com.crosspaste.db.DesktopDriverFactory
import com.crosspaste.db.DriverFactory
import com.crosspaste.db.createDatabase
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.secure.SecureDao
import com.crosspaste.db.secure.SecureIO
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.db.task.TaskDao
import com.crosspaste.headless.HeadlessPasteboardService
import com.crosspaste.headless.headlessUiModule
import com.crosspaste.headless.headlessViewModelModule
import com.crosspaste.i18n.DesktopGlobalCopywriter
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.image.DesktopFaviconLoader
import com.crosspaste.image.DesktopFileExtLoader
import com.crosspaste.image.DesktopIconColorExtractor
import com.crosspaste.image.DesktopImageHandler
import com.crosspaste.image.DesktopThumbnailLoader
import com.crosspaste.image.FaviconLoader
import com.crosspaste.image.FileExtImageLoader
import com.crosspaste.image.GenerateImageService
import com.crosspaste.image.ImageHandler
import com.crosspaste.image.OCRModule
import com.crosspaste.image.ThumbnailLoader
import com.crosspaste.image.coil.ImageLoaders
import com.crosspaste.listener.ActiveGraphicsDevice
import com.crosspaste.listener.DesktopGlobalListener
import com.crosspaste.listener.DesktopShortKeysAction
import com.crosspaste.listener.DesktopShortcutKeys
import com.crosspaste.listener.DesktopShortcutKeysListener
import com.crosspaste.listener.DesktopShortcutKeysLoader
import com.crosspaste.listener.GlobalListener
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.listener.ShortcutKeysAction
import com.crosspaste.listener.ShortcutKeysListener
import com.crosspaste.listener.ShortcutKeysLoader
import com.crosspaste.log.CrossPasteLogger
import com.crosspaste.mcp.DesktopMcpServer
import com.crosspaste.mcp.McpResourceProvider
import com.crosspaste.mcp.McpServer
import com.crosspaste.mcp.McpToolProvider
import com.crosspaste.module.ModuleDownloadManager
import com.crosspaste.module.ModuleManager
import com.crosspaste.module.ocr.DesktopOCRModule
import com.crosspaste.net.DesktopNetworkInterfaceService
import com.crosspaste.net.DesktopPasteBonjourService
import com.crosspaste.net.DesktopPasteServer
import com.crosspaste.net.DesktopResourcesClient
import com.crosspaste.net.DesktopServerFactory
import com.crosspaste.net.DesktopServerModule
import com.crosspaste.net.NetworkInterfaceService
import com.crosspaste.net.PasteBonjourService
import com.crosspaste.net.PasteClient
import com.crosspaste.net.ResourcesClient
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
import com.crosspaste.paste.CacheManager
import com.crosspaste.paste.CurrentPaste
import com.crosspaste.paste.DefaultPasteSyncProcessManager
import com.crosspaste.paste.DesktopCacheManager
import com.crosspaste.paste.DesktopCurrentPaste
import com.crosspaste.paste.DesktopGuidePasteDataService
import com.crosspaste.paste.DesktopPasteExportParamFactory
import com.crosspaste.paste.DesktopPasteImportParamFactory
import com.crosspaste.paste.DesktopPasteMenuService
import com.crosspaste.paste.DesktopPasteTagMenuService
import com.crosspaste.paste.DesktopSearchContentService
import com.crosspaste.paste.DesktopSourceExclusionService
import com.crosspaste.paste.DesktopTransferableConsumer
import com.crosspaste.paste.DesktopTransferableProducer
import com.crosspaste.paste.GuidePasteDataService
import com.crosspaste.paste.PasteExportParamFactory
import com.crosspaste.paste.PasteExportService
import com.crosspaste.paste.PasteImportParamFactory
import com.crosspaste.paste.PasteImportService
import com.crosspaste.paste.PasteSyncProcessManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.SearchContentService
import com.crosspaste.paste.TransferableConsumer
import com.crosspaste.paste.TransferableProducer
import com.crosspaste.paste.getDesktopPasteboardService
import com.crosspaste.paste.item.UpdatePasteItemHelper
import com.crosspaste.paste.plugin.process.DistinctPlugin
import com.crosspaste.paste.plugin.process.FileToUrlPlugin
import com.crosspaste.paste.plugin.process.FilesToImagesPlugin
import com.crosspaste.paste.plugin.process.GenerateTextPlugin
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
import com.crosspaste.path.DesktopMigration
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.path.getPlatformPathProvider
import com.crosspaste.platform.Platform
import com.crosspaste.presist.FilePersist
import com.crosspaste.rendering.OpenGraphService
import com.crosspaste.rendering.RenderingService
import com.crosspaste.secure.DesktopSecureStoreFactory
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.secure.SecureStore
import com.crosspaste.secure.SecureStoreFactory
import com.crosspaste.share.AppShareService
import com.crosspaste.share.DesktopAppShareService
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
import com.crosspaste.sync.SyncResolver
import com.crosspaste.sync.TokenCache
import com.crosspaste.sync.TokenCacheApi
import com.crosspaste.task.CleanPasteTaskExecutor
import com.crosspaste.task.CleanTaskTaskExecutor
import com.crosspaste.task.DelayedDeletePasteTaskExecutor
import com.crosspaste.task.DeletePasteTaskExecutor
import com.crosspaste.task.DesktopTaskSubmitter
import com.crosspaste.task.OpenGraphTaskExecutor
import com.crosspaste.task.PullFileTaskExecutor
import com.crosspaste.task.PullIconTaskExecutor
import com.crosspaste.task.SwitchLanguageTaskExecutor
import com.crosspaste.task.SyncPasteTaskExecutor
import com.crosspaste.task.TaskExecutor
import com.crosspaste.task.TaskSubmitter
import com.crosspaste.ui.DesktopFontManager
import com.crosspaste.ui.DesktopScreenProvider
import com.crosspaste.ui.NavigationManager
import com.crosspaste.ui.ScreenProvider
import com.crosspaste.ui.base.DesktopIconStyle
import com.crosspaste.ui.base.DesktopNotificationManager
import com.crosspaste.ui.base.DesktopUISupport
import com.crosspaste.ui.base.FontManager
import com.crosspaste.ui.base.IconStyle
import com.crosspaste.ui.base.MenuHelper
import com.crosspaste.ui.base.SmartImageDisplayStrategy
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.devices.DesktopDeviceScopeFactory
import com.crosspaste.ui.devices.DesktopSyncScopeFactory
import com.crosspaste.ui.devices.DeviceScopeFactory
import com.crosspaste.ui.devices.SyncScopeFactory
import com.crosspaste.ui.model.GeneralPasteSearchViewModel
import com.crosspaste.ui.model.MarketingPasteData
import com.crosspaste.ui.model.MarketingPasteSearchViewModel
import com.crosspaste.ui.model.PasteSearchViewModel
import com.crosspaste.ui.model.PasteSelectionViewModel
import com.crosspaste.ui.settings.DesktopStoragePathManager
import com.crosspaste.ui.settings.StoragePathManager
import com.crosspaste.ui.theme.DesktopThemeDetector
import com.crosspaste.ui.theme.ThemeDetector
import com.crosspaste.utils.DesktopLocaleUtils
import com.crosspaste.utils.DeviceUtils
import com.crosspaste.utils.LocaleUtils
import com.crosspaste.utils.getAppEnvUtils
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import com.github.kwhat.jnativehook.mouse.NativeMouseListener
import io.github.oshai.kotlinlogging.KLogger
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import okio.Path
import org.koin.core.KoinApplication
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.awt.image.BufferedImage

class DesktopModule(
    private val appEnv: AppEnv,
    private val appPathProvider: AppPathProvider,
    private val configManager: DesktopConfigManager,
    private val crossPasteLogger: CrossPasteLogger,
    private val deviceUtils: DeviceUtils,
    private val klogger: KLogger,
    private val platform: Platform,
    private val headless: Boolean = false,
) : CrossPasteModule {

    val marketingMode = getAppEnvUtils().isDevelopment() && DevConfig.marketingMode

    override fun appModule() =
        module {
            single<AppControl> { DesktopAppControl(get()) }
            single<AppEnv> { appEnv }
            single<AppExitService> { DesktopAppExitService }
            single<AppInfo> { get<AppInfoFactory>().createAppInfo() }
            single<AppInfoFactory> { DesktopAppInfoFactory(get()) }
            single<AppLaunchState> { get<DesktopAppLaunchState>() }
            single<AppLock> { get<DesktopAppLaunch>() }
            single<AppPathProvider> { appPathProvider }
            single<AppRestartService> { DesktopAppRestartService(get(), get()) }
            single<AppStartUpService> { DesktopAppStartUpService(get(), get(), get(), get()) }
            single<AppUpdateService> { DesktopAppUpdateService(get(), get(), get(), get(), get()) }
            single<AppUrls> { DesktopAppUrls }
            single<CacheManager> { DesktopCacheManager(get(), get()) }
            @Suppress("UNCHECKED_CAST")
            single<CommonConfigManager> { configManager as CommonConfigManager }
            single<CrossPasteLogger> { crossPasteLogger }
            single<DesktopAppLaunch> { DesktopAppLaunch(get(), get()) }
            single<DesktopAppLaunchState> { runBlocking { get<DesktopAppLaunch>().launch() } }
            single<DesktopConfigManager> { configManager }
            single<DesktopMigration> { DesktopMigration(get(), get(), get(), get()) }
            single<ModuleDownloadManager> { ModuleDownloadManager(get(), get()) }
            single<ModuleManager> {
                val ocrModule = get<OCRModule>()
                ModuleManager(
                    mapOf(
                        ocrModule.moduleId to ocrModule,
                    ),
                )
            }
            single<DeviceUtils> { deviceUtils }
            single<EndpointInfoFactory> { EndpointInfoFactory(get(), lazy { get<Server>() }, get()) }
            single<FileExtImageLoader> { DesktopFileExtLoader(get(), get(), get()) }
            single<FilePersist> { FilePersist }
            single<ImageHandler<BufferedImage>> { DesktopImageHandler }
            single<ImageLoaders> { ImageLoaders(get(), get(), get(), get(), get(), get()) }
            single<KLogger> { klogger }
            single<LocaleUtils> { DesktopLocaleUtils }
            single<QRCodeGenerator> { DesktopQRCodeGenerator(get(), get(), get()) }
            single<ReadWriteConfig<Int>>(named("readWritePort")) { ReadWritePort(get()) }
            single<AppShareService> { DesktopAppShareService(get(), get(), get(), get()) }
            single<Platform> { platform }
            single<SimpleConfigFactory> { DesktopSimpleConfigFactory(get()) }
            single<SyncInfoFactory> { SyncInfoFactory(get(), get()) }
            single<ThumbnailLoader> { DesktopThumbnailLoader(get(), get()) }
            single<UserDataPathProvider> { UserDataPathProvider(get(), getPlatformPathProvider(get())) }
            single<FontManager> { DesktopFontManager(get()) }
        }

    override fun extensionModule() =
        module {
            single<OCRModule> { DesktopOCRModule(get(), get(), get(), get(), get()) }
        }

    // SqlDelight
    override fun sqlDelightModule() =
        module {
            single<DriverFactory> { DesktopDriverFactory(get()) }
            single<Database> { createDatabase(get()) }
            single<PasteDao> {
                PasteDao(
                    appInfo = get(),
                    commonConfigManager = get(),
                    currentPaste = get(),
                    database = get(),
                    pasteProcessPlugins =
                        listOf(
                            RemoveInvalidPlugin,
                            DistinctPlugin(get()),
                            GenerateTextPlugin,
                            GenerateUrlPlugin,
                            TextToColorPlugin,
                            FilesToImagesPlugin(get()),
                            FileToUrlPlugin(get()),
                            RemoveFolderImagePlugin(get()),
                            RemoveHtmlImagePlugin(get()),
                            SortPlugin,
                        ),
                    searchContentService = get(),
                    taskSubmitter = get(),
                    userDataPathProvider = get(),
                )
            }
            single<SecureIO> { SecureDao(get()) }
            single<SyncRuntimeInfoDao> { SyncRuntimeInfoDao(get()) }
            single<TaskDao> { TaskDao(get()) }
        }

    // NetworkModule.kt
    override fun networkModule() =
        module {
            single<ExceptionHandler> { DesktopExceptionHandler() }
            single<FaviconLoader> { DesktopFaviconLoader(get(), get()) }
            single<McpToolProvider> { McpToolProvider(get(), get(), get()) }
            single<McpResourceProvider> { McpResourceProvider(get()) }
            single<McpServer> {
                DesktopMcpServer(
                    mcpPort = (get<DesktopConfigManager>().getCurrentConfig()).mcpServerPort,
                    mcpToolProvider = get(),
                    mcpResourceProvider = get(),
                )
            }
            single<NearbyDeviceManager> {
                if (marketingMode) {
                    MarketingNearbyDeviceManager()
                } else {
                    GeneralNearbyDeviceManager(get(), get(), get(), get())
                }
            }
            single<NetworkInterfaceService> { DesktopNetworkInterfaceService(get()) }
            single<ResourcesClient> { DesktopResourcesClient(get(), get()) }
            single<PasteBonjourService> { DesktopPasteBonjourService(get(), get(), get(), get()) }
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
                    appControl = get(),
                    appInfo = get(),
                    appTokenApi = get(),
                    cacheManager = get(),
                    exceptionHandler = get(),
                    nearbyDeviceManager = get(),
                    networkInterfaceService = get(),
                    pasteboardService = get(),
                    secureKeyPairSerializer = get(),
                    secureStore = get(),
                    syncApi = get(),
                    syncInfoFactory = get(),
                    syncRoutingApi = get(),
                    serverEncryptPluginFactory = get(),
                    serverDecryptionPluginFactory = get(),
                    userDataPathProvider = get(),
                )
            }
            single<SyncApi> { SyncApi }
            single<SyncClientApi> { SyncClientApi(get(), get(), get(), get(), get()) }
            single<SyncManager> {
                if (marketingMode) {
                    MarketingSyncManager()
                } else {
                    GeneralSyncManager(
                        syncResolver = get(),
                        syncRuntimeInfoDao = get(),
                    )
                }
            }
            single<SyncResolver> {
                SyncResolver(
                    lazyPasteBonjourService = lazy { get() },
                    networkInterfaceService = get(),
                    ratingPromptManager = get(),
                    secureStore = get(),
                    syncClientApi = get(),
                    syncInfoFactory = get(),
                    syncRuntimeInfoDao = get(),
                    telnetHelper = get(),
                    tokenCache = get(),
                )
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
            single<SecureStoreFactory> { DesktopSecureStoreFactory(get(), get(), get(), get(), get()) }
            single<ServerDecryptionPluginFactory> { ServerDecryptionPluginFactory(get()) }
            single<ServerEncryptPluginFactory> { ServerEncryptPluginFactory(get()) }
        }

    // PasteTypePluginModule.kt
    override fun pasteTypePluginModule() =
        module {
            single<ColorTypePlugin> { DesktopColorTypePlugin() }
            single<FilesTypePlugin> { DesktopFilesTypePlugin(get(), get(), get(), get()) }
            single<HtmlTypePlugin> { DesktopHtmlTypePlugin(get()) }
            single<ImageTypePlugin> { DesktopImageTypePlugin(get(), get(), get(), get()) }
            single<RtfTypePlugin> { DesktopRtfTypePlugin() }
            single<TextTypePlugin> { DesktopTextTypePlugin() }
            single<UrlTypePlugin> { DesktopUrlTypePlugin(get()) }
        }

    // PasteComponentModule.kt
    override fun pasteComponentModule() =
        module {
            single<CleanScheduler> { CleanScheduler(get(), get(), get()) }
            single<CurrentPaste> { DesktopCurrentPaste(lazy { get() }) }
            single<RenderingService<String>>(named("urlRendering")) {
                OpenGraphService(get(), get<ImageHandler<BufferedImage>>(), get(), get(), get())
            }
            single<DesktopPasteMenuService> {
                DesktopPasteMenuService(get(), get(), get(), get(), get(), get(), get(), get(), get())
            }
            single<DesktopPasteTagMenuService> {
                DesktopPasteTagMenuService(get(), get())
            }
            single<GenerateImageService> { GenerateImageService() }
            single<GuidePasteDataService> { DesktopGuidePasteDataService(get(), get(), get(), get(), get()) }
            single<DesktopSourceExclusionService> { DesktopSourceExclusionService(get()) }
            single<PasteboardService> {
                if (headless) {
                    HeadlessPasteboardService(get(), get())
                } else {
                    getDesktopPasteboardService(get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
                }
            }
            single<PasteExportParamFactory<Path>> { DesktopPasteExportParamFactory() }
            single<PasteExportService> { PasteExportService(get(), get(), get()) }
            single<PasteImportParamFactory<Path>> { DesktopPasteImportParamFactory() }
            single<PasteImportService> { PasteImportService(get(), get(), get(), get()) }
            single<PasteSyncProcessManager<Long>> { DefaultPasteSyncProcessManager() }
            single<SearchContentService> { DesktopSearchContentService() }
            single<TaskExecutor> {
                TaskExecutor(
                    listOf(
                        CleanPasteTaskExecutor(get(), get()),
                        CleanTaskTaskExecutor(get()),
                        DelayedDeletePasteTaskExecutor(get()),
                        DeletePasteTaskExecutor(get()),
                        OpenGraphTaskExecutor(
                            lazy { get<RenderingService<String>>(named("urlRendering")) },
                            get(),
                        ),
                        PullFileTaskExecutor(get(), get(), get(), get(), get(), get(), get()),
                        PullIconTaskExecutor(get(), get(), get(), get()),
                        SwitchLanguageTaskExecutor(get(), get()),
                        SyncPasteTaskExecutor(get(), get(), get(), get(), get(), get()),
                    ),
                    get(),
                )
            }
            single<TaskSubmitter> { DesktopTaskSubmitter(get(), get(), get(), lazy { get() }) }
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
            single<UpdatePasteItemHelper> {
                UpdatePasteItemHelper(get(), get())
            }
        }

    // UIModule.kt
    override fun uiModule() =
        module {
            single<ActiveGraphicsDevice> { get<DesktopAppSize>() }
            single<AppFileChooser> { DesktopAppFileChooser(get()) }
            single<AppSize> { get<DesktopAppSize>() }
            single<AppTokenApi> { DesktopAppTokenService(get()) }
            single<AppWindowManager> { get<DesktopAppWindowManager>() }
            single<DesktopAppSize> { DesktopAppSize(get()) }
            single<DesktopAppWindowManager> {
                getDesktopAppWindowManager(get(), lazy { get() }, lazy { get() }, get(), get())
            }
            single<DesktopIconColorExtractor> { DesktopIconColorExtractor(get()) }
            single<DesktopScreenProvider> { DesktopScreenProvider(get(), get(), get(), get(), get(), get()) }
            single<DesktopShortcutKeysListener> { DesktopShortcutKeysListener(get(), get()) }
            single<DeviceScopeFactory> { DesktopDeviceScopeFactory() }
            single<GlobalCopywriter> { DesktopGlobalCopywriter(get(), lazy { get() }, get()) }
            single<GlobalListener> { DesktopGlobalListener(get(), get(), get(), get()) }
            single<IconStyle> { DesktopIconStyle(get()) }
            single<MenuHelper> { MenuHelper(get(), get(), get(), get(), get()) }
            single<NavigationManager> { get<DesktopScreenProvider>() }
            single<NativeKeyListener> { get<DesktopShortcutKeysListener>() }
            single<NativeMouseListener> { get<DesktopAppSize>() }
            single<NotificationManager> { DesktopNotificationManager(get(), get(), get(), get()) }
            single<PlatformContext> { PlatformContext.INSTANCE }
            single<RatingPromptManager> { DesktopRatingPromptManager() }
            single<ScreenProvider> { get<DesktopScreenProvider>() }
            single<ShortcutKeys> { DesktopShortcutKeys(get(), get(), get()) }
            single<ShortcutKeysAction> {
                DesktopShortKeysAction(get(), get(), get(), get(), get(), get(), get())
            }
            single<ShortcutKeysListener> { get<DesktopShortcutKeysListener>() }
            single<ShortcutKeysLoader> { DesktopShortcutKeysLoader(get(), get()) }
            single<SmartImageDisplayStrategy> { SmartImageDisplayStrategy() }
            single<SoundService> { DesktopSoundService(get()) }
            single<StoragePathManager> { DesktopStoragePathManager() }
            single<SyncScopeFactory> { DesktopSyncScopeFactory() }
            single<ThemeDetector> { DesktopThemeDetector(get()) }
            single<TokenCacheApi> { TokenCache }
            single<UISupport> {
                DesktopUISupport(
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get<DesktopAppWindowManager>(),
                )
            }
        }

    // ViewModelModule.kt
    override fun viewModelModule() =
        module {
            single<MarketingPasteData> { MarketingPasteData(get(), get()) }
            single<PasteSearchViewModel> {
                if (marketingMode) {
                    MarketingPasteSearchViewModel(get())
                } else {
                    GeneralPasteSearchViewModel(get(), get())
                }
            }
            single<PasteSelectionViewModel> { PasteSelectionViewModel(get(), get(), get()) }
        }

    // Application.kt
    fun initKoinApplication(): KoinApplication =
        GlobalContext.startKoin {
            if (headless) {
                modules(
                    appModule(),
                    extensionModule(),
                    sqlDelightModule(),
                    networkModule(),
                    securityModule(),
                    pasteTypePluginModule(),
                    pasteComponentModule(),
                    headlessUiModule(),
                    headlessViewModelModule(),
                )
            } else {
                modules(
                    appModule(),
                    extensionModule(),
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
