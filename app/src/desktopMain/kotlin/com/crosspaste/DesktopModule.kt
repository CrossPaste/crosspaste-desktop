package com.crosspaste

import com.crosspaste.app.AppEnv
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.config.DevConfig
import com.crosspaste.db.DesktopDriverFactory
import com.crosspaste.db.DriverFactory
import com.crosspaste.db.createDatabase
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.paste.PasteTagDao
import com.crosspaste.db.paste.SqlPasteTagDao
import com.crosspaste.db.secure.SecureIO
import com.crosspaste.db.secure.SqlSecureDao
import com.crosspaste.db.sync.SqlSyncRuntimeInfoDao
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.db.task.SqlTaskDao
import com.crosspaste.db.task.TaskDao
import com.crosspaste.headless.headlessUiModule
import com.crosspaste.headless.headlessViewModelModule
import com.crosspaste.image.OCRModule
import com.crosspaste.log.CrossPasteLogger
import com.crosspaste.module.ocr.DesktopOCRModule
import com.crosspaste.net.plugin.ClientDecryptPlugin
import com.crosspaste.net.plugin.ClientEncryptPlugin
import com.crosspaste.net.plugin.ServerDecryptionPluginFactory
import com.crosspaste.net.plugin.ServerEncryptPluginFactory
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
import com.crosspaste.platform.Platform
import com.crosspaste.secure.DesktopSecureStoreFactory
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.secure.SecureStore
import com.crosspaste.secure.SecureStoreFactory
import com.crosspaste.ui.model.GeneralPasteSearchViewModel
import com.crosspaste.ui.model.MarketingPasteData
import com.crosspaste.ui.model.MarketingPasteSearchViewModel
import com.crosspaste.ui.model.PasteSearchViewModel
import com.crosspaste.ui.model.PasteSelectionViewModel
import com.crosspaste.utils.DeviceUtils
import com.crosspaste.utils.getAppEnvUtils
import io.github.oshai.kotlinlogging.KLogger
import org.koin.core.KoinApplication
import org.koin.core.context.GlobalContext
import org.koin.dsl.module

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
        desktopAppModule(
            appEnv,
            appPathProvider,
            configManager,
            crossPasteLogger,
            deviceUtils,
            klogger,
            platform,
        )

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
                    database = get(),
                    searchContentService = get(),
                    taskSubmitter = get(),
                    userDataPathProvider = get(),
                )
            }
            single<PasteTagDao> { SqlPasteTagDao(get()) }
            single<SecureIO> { SqlSecureDao(get()) }
            single<SyncRuntimeInfoDao> { SqlSyncRuntimeInfoDao(get()) }
            single<TaskDao> { SqlTaskDao(get()) }
        }

    override fun networkModule() = desktopNetworkModule(marketingMode)

    // SecurityModule
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

    // PasteTypePluginModule
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

    override fun pasteComponentModule() = desktopPasteComponentModule(headless)

    override fun uiModule() = desktopUiModule()

    // ViewModelModule
    override fun viewModelModule() =
        module {
            single<MarketingPasteData> { MarketingPasteData(get(), get()) }
            single<PasteSearchViewModel> {
                if (marketingMode) {
                    MarketingPasteSearchViewModel(get())
                } else {
                    GeneralPasteSearchViewModel(get(), get(), get())
                }
            }
            single<PasteSelectionViewModel> { PasteSelectionViewModel(get(), get(), get()) }
        }

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
