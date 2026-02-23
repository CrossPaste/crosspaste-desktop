package com.crosspaste

import com.crosspaste.app.AppControl
import com.crosspaste.app.AppEnv
import com.crosspaste.app.AppExitService
import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppInfoFactory
import com.crosspaste.app.AppLaunchState
import com.crosspaste.app.AppLock
import com.crosspaste.app.AppRestartService
import com.crosspaste.app.AppStartUpService
import com.crosspaste.app.AppUpdateService
import com.crosspaste.app.AppUrls
import com.crosspaste.app.DesktopAppControl
import com.crosspaste.app.DesktopAppExitService
import com.crosspaste.app.DesktopAppInfoFactory
import com.crosspaste.app.DesktopAppLaunch
import com.crosspaste.app.DesktopAppLaunchState
import com.crosspaste.app.DesktopAppRestartService
import com.crosspaste.app.DesktopAppStartUpService
import com.crosspaste.app.DesktopAppUpdateService
import com.crosspaste.app.DesktopAppUrls
import com.crosspaste.app.EndpointInfoFactory
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.config.DesktopSimpleConfigFactory
import com.crosspaste.config.ReadWriteConfig
import com.crosspaste.config.ReadWritePort
import com.crosspaste.config.SimpleConfigFactory
import com.crosspaste.image.DesktopFileExtLoader
import com.crosspaste.image.DesktopImageHandler
import com.crosspaste.image.DesktopThumbnailLoader
import com.crosspaste.image.FileExtImageLoader
import com.crosspaste.image.ImageHandler
import com.crosspaste.image.ThumbnailLoader
import com.crosspaste.image.coil.ImageLoaders
import com.crosspaste.log.CrossPasteLogger
import com.crosspaste.module.ModuleDownloadManager
import com.crosspaste.module.ModuleManager
import com.crosspaste.net.Server
import com.crosspaste.net.SyncInfoFactory
import com.crosspaste.paste.CacheManager
import com.crosspaste.paste.DesktopCacheManager
import com.crosspaste.path.AppPathProvider
import com.crosspaste.path.DesktopMigration
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.path.getPlatformPathProvider
import com.crosspaste.platform.Platform
import com.crosspaste.presist.FilePersist
import com.crosspaste.share.AppShareService
import com.crosspaste.share.DesktopAppShareService
import com.crosspaste.sync.DesktopQRCodeGenerator
import com.crosspaste.sync.QRCodeGenerator
import com.crosspaste.ui.DesktopFontManager
import com.crosspaste.ui.base.FontManager
import com.crosspaste.utils.DesktopLocaleUtils
import com.crosspaste.utils.DeviceUtils
import com.crosspaste.utils.LocaleUtils
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.runBlocking
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.awt.image.BufferedImage

@Suppress("UNCHECKED_CAST")
fun desktopAppModule(
    appEnv: AppEnv,
    appPathProvider: AppPathProvider,
    configManager: DesktopConfigManager,
    crossPasteLogger: CrossPasteLogger,
    deviceUtils: DeviceUtils,
    klogger: KLogger,
    platform: Platform,
): Module =
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
        single<CommonConfigManager> { configManager as CommonConfigManager }
        single<CrossPasteLogger> { crossPasteLogger }
        single<DesktopAppLaunch> { DesktopAppLaunch(get(), get()) }
        single<DesktopAppLaunchState> { runBlocking { get<DesktopAppLaunch>().launch() } }
        single<DesktopConfigManager> { configManager }
        single<DesktopMigration> { DesktopMigration(get(), get(), get(), get()) }
        single<ModuleDownloadManager> { ModuleDownloadManager(get(), get()) }
        single<ModuleManager> {
            val ocrModule = get<com.crosspaste.image.OCRModule>()
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
