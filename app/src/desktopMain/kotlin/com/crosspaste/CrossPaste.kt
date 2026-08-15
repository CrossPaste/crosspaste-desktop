package com.crosspaste

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.application
import androidx.navigation.compose.rememberNavController
import com.crosspaste.app.AppExitService
import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppLaunchState
import com.crosspaste.app.AppLock
import com.crosspaste.app.AppName
import com.crosspaste.app.AppStartUpService
import com.crosspaste.app.AppUpdateService
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.DesktopPidFileService
import com.crosspaste.app.ExitMode
import com.crosspaste.app.NativeMessagingHostService
import com.crosspaste.bootstrap.DesktopBootstrap
import com.crosspaste.clean.CleanScheduler
import com.crosspaste.cli.CliServer
import com.crosspaste.cli.CliSymlinkService
import com.crosspaste.config.AppMetadataRepository
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.db.DriverFactory
import com.crosspaste.listener.GlobalListener
import com.crosspaste.log.DesktopCrossPasteLogger
import com.crosspaste.mcp.McpServer
import com.crosspaste.net.PasteBonjourService
import com.crosspaste.net.PasteClient
import com.crosspaste.net.ResourcesClient
import com.crosspaste.net.Server
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.GuidePasteDataService
import com.crosspaste.paste.PasteboardService
import com.crosspaste.path.DesktopAppPathProvider
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.FilePersist
import com.crosspaste.rendering.RenderingService
import com.crosspaste.sync.PastePullService
import com.crosspaste.sync.QRCodeGenerator
import com.crosspaste.sync.SyncManager
import com.crosspaste.task.TaskExecutor
import com.crosspaste.ui.CrossPasteWindows
import com.crosspaste.ui.LocalAppSizeValueState
import com.crosspaste.ui.LocalDesktopAppSizeValueState
import com.crosspaste.ui.LocalExitApplication
import com.crosspaste.ui.LocalNavHostController
import com.crosspaste.ui.theme.DesktopTheme
import com.crosspaste.utils.DesktopDeviceUtils
import com.crosspaste.utils.DesktopLocaleUtils
import com.crosspaste.utils.GlobalCoroutineScope.ioCoroutineDispatcher
import com.crosspaste.utils.getAppEnvUtils
import com.crosspaste.utils.getPlatformUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.skiko.SkikoProperties
import org.koin.compose.koinInject
import org.koin.core.KoinApplication
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

class CrossPaste {

    companion object {

        private val appEnvUtils = getAppEnvUtils()

        private val appEnv = appEnvUtils.getCurrentAppEnv()

        private val platform = getPlatformUtils().platform

        private val appPathProvider = DesktopAppPathProvider(platform)

        private val deviceUtils = DesktopDeviceUtils(platform)

        private val localeUtils = DesktopLocaleUtils

        private val configFilePersist =
            FilePersist.createOneFilePersist(
                appPathProvider.resolve("appConfig.json", AppFileType.USER),
            )

        private val metadataFilePersist =
            FilePersist.createOneFilePersist(
                appPathProvider.resolve(".metadata", AppFileType.USER),
            )

        init {
            DesktopBootstrap.preClassLoad(
                appPathProvider = appPathProvider,
                metadataFilePersist = metadataFilePersist,
                configFilePersist = configFilePersist,
            )
        }

        private val appMetadataRepository =
            AppMetadataRepository(metadataFilePersist, deviceUtils)

        private val configManager =
            DesktopConfigManager(configFilePersist, localeUtils)

        private val crossPasteLogger =
            DesktopCrossPasteLogger(
                appPathProvider.resolve("crosspaste.log", AppFileType.LOG).toString(),
                configManager,
            )

        private val logger: KLogger = KotlinLogging.logger {}

        var headless: Boolean = false
            private set

        private lateinit var module: DesktopModule

        lateinit var koinApplication: KoinApplication
            private set

        private fun initModule() {
            module =
                DesktopModule(
                    appEnv,
                    appMetadataRepository,
                    appPathProvider,
                    configManager,
                    crossPasteLogger,
                    deviceUtils,
                    logger,
                    platform,
                    headless,
                )
            koinApplication = module.initKoinApplication()
        }

        @Throws(Exception::class)
        private suspend fun startApplication() {
            runCatching {
                if (appEnvUtils.isProduction()) {
                    System.setProperty("jna.library.path", appPathProvider.pasteAppExePath.toString())
                }
                if (!headless) {
                    // Initialize AWT Toolkit to prevent potential deadlock issues
                    java.awt.Toolkit.getDefaultToolkit()
                }

                val koin = koinApplication.koin
                val appLaunchState = koin.get<AppLaunchState>()
                if (appLaunchState.acquiredLock) {
                    val configManager = koin.get<DesktopConfigManager>()
                    val notificationManager = koin.get<NotificationManager>()
                    configManager.notificationManager = notificationManager
                    if (configManager.getCurrentConfig().enablePasteboardListening) {
                        koin.get<PasteboardService>().start()
                    }
                    koin.get<QRCodeGenerator>()
                    koin.get<SyncManager>().start()
                    koin.get<PastePullService>().init()
                    koin.get<Server>().start()
                    ioCoroutineDispatcher.launch { koin.get<CliServer>().start() }
                    if (configManager.getCurrentConfig().enableMcpServer) {
                        ioCoroutineDispatcher.launch { koin.get<McpServer>().start() }
                    }
                    koin.get<PasteClient>()
                    koin.get<PasteBonjourService>()
                    koin.get<CleanScheduler>().start()
                    koin.get<DesktopPidFileService>().start()
                    koin.get<AppUpdateService>().start()

                    if (!headless) {
                        // These only serve an interactive desktop session: OS login-item
                        // autostart would relaunch the GUI app on login, native-messaging
                        // manifests target local browsers, guide pastes are onboarding
                        // content, and the CLI symlink probe drives the install prompt
                        // and the extension-page entry.
                        koin.get<AppStartUpService>().followConfig()
                        koin.get<NativeMessagingHostService>().register()
                        koin.get<GuidePasteDataService>().initData()
                        ioCoroutineDispatcher.launch { koin.get<CliSymlinkService>().refresh() }

                        koin.get<DesktopAppWindowManager>().startWindowService()
                        FileKit.init(appId = AppName)
                    }

                    ioCoroutineDispatcher.launch {
                        val jobs =
                            listOf(
                                async(ioDispatcher) {
                                    koin.get<RenderingService<String>>(named("urlRendering")).start()
                                },
                            )

                        jobs.awaitAll()
                    }
                } else {
                    // The GUI second instance keeps its historic silent exit(0); a
                    // headless daemon must fail loudly with a non-zero code so that
                    // service managers (systemd) and scripts can detect the conflict.
                    if (headless) {
                        val runningPid = readRunningPid()?.let { " (pid $it)" }.orEmpty()
                        System.err.println(
                            "Error: another CrossPaste instance is already running$runningPid. " +
                                "Only one CrossPaste process (desktop app or headless daemon) can run per user.",
                        )
                        exitProcess(1)
                    }
                    exitProcess(0)
                }
            }.onFailure { e ->
                logger.error(e) { "cant start crosspaste" }
                if (headless) {
                    // The shutdown hook is only registered later in runHeadless(), so
                    // run the exit hooks (e.g. pid-file removal) best-effort here; if
                    // the failure happened before any hook was registered the list is
                    // simply empty.
                    runCatching {
                        koinApplication.koin
                            .get<AppExitService>()
                            .beforeExitList
                            .forEach { hook -> runCatching { hook.invoke() } }
                    }
                    System.err.println("Error: CrossPaste failed to start in headless mode: ${e.message}")
                    exitProcess(1)
                }
                exitProcess(0)
            }
        }

        private fun readRunningPid(): String? =
            runCatching {
                koinApplication.koin
                    .get<DesktopPidFileService>()
                    .pidFilePath
                    .toFile()
                    .readText()
                    .trim()
                    .takeIf { it.isNotEmpty() }
            }.getOrNull()

        private suspend fun exitCrossPasteApplication(
            exitMode: ExitMode,
            exitApplication: () -> Unit,
        ) {
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
            if (!headless && exitMode == ExitMode.MIGRATION) {
                val appWindowManager = koin.get<DesktopAppWindowManager>()
                appWindowManager.hideMainWindow()
            }
            exitApplication()
        }

        private suspend fun shutdownAllServices() {
            withTimeoutOrNull(5.seconds) {
                supervisorScope {
                    val jobs =
                        buildList {
                            add(async { stopService<AppUpdateService>("AppUpdateService") { it.stop() } })
                            add(async { stopService<TaskExecutor>("TaskExecutor") { it.shutdown() } })
                            add(
                                async {
                                    stopService<RenderingService<String>>(
                                        qualifier = named("urlRendering"),
                                        serviceName = "RenderingService",
                                    ) { it.stop() }
                                },
                            )
                            add(async { stopService<PasteboardService>("PasteboardService") { it.stop() } })
                            add(async { stopService<PasteBonjourService>("PasteBonjourService") { it.close() } })
                            add(async { stopService<Server>("PasteServer") { it.stop() } })
                            add(async { stopService<CliServer>("CliServer") { it.stop() } })
                            add(async { stopService<McpServer>("McpServer") { it.stop() } })
                            add(async { stopService<SyncManager>("SyncManager") { it.stop() } })
                            add(async { stopService<CleanScheduler>("CleanPasteScheduler") { it.stop() } })
                            if (!headless) {
                                add(
                                    async {
                                        stopService<DesktopAppWindowManager>("DesktopAppWindowManager") {
                                            it.stopWindowService()
                                        }
                                    },
                                )
                                add(async { stopService<GlobalListener>("GlobalListener") { it.stop() } })
                            }
                            add(
                                async {
                                    stopService<UserDataPathProvider>("UserDataPathProvider") { it.cleanTemp() }
                                },
                            )
                            add(async { stopService<PasteClient>("PasteClient") { it.close() } })
                            add(async { stopService<ResourcesClient>("ResourcesClient") { it.close() } })
                        }

                    jobs.awaitAll()
                }
            }

            runCatching {
                stopService<DriverFactory>("DriverFactory") { it.closeDriver() }
            }
        }

        private inline fun <reified T : Any> stopService(
            serviceName: String,
            qualifier: Qualifier? = null,
            stopAction: (T) -> Unit,
        ) {
            runCatching {
                val service = koinApplication.koin.get<T>(qualifier = qualifier)
                stopAction(service)
            }.onSuccess {
                logger.info { "$serviceName stop completed" }
            }.onFailure { e ->
                logger.error(e) { "Error stopping $serviceName" }
            }
        }

        private fun runHeadless() {
            logger.info { "Running in headless mode" }
            val latch = java.util.concurrent.CountDownLatch(1)
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    logger.info { "Shutdown signal received" }
                    val koin = koinApplication.koin
                    // Mirror exitCrossPasteApplication(): run the exit hooks around
                    // shutdownAllServices() so that cleanup registered via
                    // AppExitService (e.g. the pid-file removal) also happens on the
                    // daemon path. Each hook is guarded so one failure cannot skip
                    // the remaining cleanup or the lock release.
                    val appExitService = koin.get<AppExitService>()
                    appExitService.beforeExitList.forEach { hook ->
                        runCatching { hook.invoke() }
                            .onFailure { e -> logger.error(e) { "beforeExit hook failed" } }
                    }
                    runBlocking { shutdownAllServices() }
                    appExitService.beforeReleaseLockList.forEach { hook ->
                        runCatching { hook.invoke() }
                            .onFailure { e -> logger.error(e) { "beforeReleaseLock hook failed" } }
                    }
                    koin.get<AppLock>().releaseLock()
                    latch.countDown()
                },
            )
            latch.await()
        }

        @OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
        @JvmStatic
        fun main(args: Array<String>) {
            headless = args.contains("--headless") || java.awt.GraphicsEnvironment.isHeadless()
            DesktopBootstrap.preStart(logger)
            initModule()

            logger.info { "Starting CrossPaste${if (headless) " (headless)" else ""}" }
            runBlocking { startApplication() }
            logger.info { "CrossPaste started" }

            if (headless) {
                runHeadless()
            } else {
                // Reading SkikoProperties loads skiko; keep it off the headless path.
                logger.info { "SkikoProperties.renderApi=${SkikoProperties.renderApi}" }

                application {
                    val appWindowManager = koinInject<DesktopAppWindowManager>()
                    val appSize = koinInject<DesktopAppSize>()
                    val globalListener = koinInject<GlobalListener>()

                    val appSizeValue by appSize.appSizeValue.collectAsState()

                    var exiting by remember { mutableStateOf(false) }

                    val exitApplication: (ExitMode) -> Unit = { mode ->
                        exiting = true
                        if (mode == ExitMode.EXIT || mode == ExitMode.RESTART) {
                            appWindowManager.hideMainWindow()
                        }
                        appWindowManager.hideSearchWindow()
                        ioCoroutineDispatcher.launch {
                            exitCrossPasteApplication(mode) { exitApplication() }
                        }
                    }

                    val navController = rememberNavController()

                    LaunchedEffect(Unit) {
                        if (!globalListener.isRegistered()) {
                            globalListener.start()
                        }
                    }

                    CompositionLocalProvider(
                        LocalAppSizeValueState provides appSizeValue,
                        LocalDesktopAppSizeValueState provides appSizeValue,
                        LocalExitApplication provides exitApplication,
                        LocalNavHostController provides navController,
                    ) {
                        DesktopTheme {
                            CrossPasteWindows(exiting)
                        }
                    }
                }
            }
        }
    }
}
