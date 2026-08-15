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
                    if (headless) {
                        // Register as soon as the single-instance lock is held (and
                        // never earlier: a second instance must not run this hook and
                        // delete the running daemon's files): from here on SIGTERM and
                        // the startup-failure exitProcess(1) run the same shutdown
                        // chain even while services are still starting.
                        registerHeadlessShutdownHook()
                    }
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
                    if (headless) {
                        // The UDS CLI API is the daemon's only control plane, so a
                        // CliServer startup failure is a daemon startup failure
                        // (fail-closed, propagates to the headless exit-1 path).
                        koin.get<CliServer>().start()
                    } else {
                        // The GUI tolerates a degraded CLI and keeps running.
                        ioCoroutineDispatcher.launch {
                            runCatching { koin.get<CliServer>().start() }
                                .onFailure { logger.warn { "CLI unavailable for this session" } }
                        }
                    }
                    if (configManager.getCurrentConfig().enableMcpServer) {
                        ioCoroutineDispatcher.launch { koin.get<McpServer>().start() }
                    }
                    koin.get<PasteClient>()
                    koin.get<PasteBonjourService>()
                    koin.get<CleanScheduler>().start()
                    koin.get<DesktopPidFileService>().start()

                    if (!headless) {
                        // These only serve an interactive desktop session: OS login-item
                        // autostart would relaunch the GUI app on login, native-messaging
                        // manifests target local browsers, guide pastes are onboarding
                        // content, the CLI symlink probe drives the install prompt and
                        // the extension-page entry, and the update service drives the
                        // in-app update UI (its DesktopAppUpdateService implementation
                        // requires the GUI-only DesktopAppWindowManager binding; server
                        // updates are an OS package-manager concern).
                        koin.get<AppUpdateService>().start()
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
                    // exitProcess(1) triggers the headless shutdown hook (registered
                    // once the lock was acquired), which runs the full cleanup chain;
                    // before lock acquisition there is nothing to clean up.
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
                                add(async { stopService<AppUpdateService>("AppUpdateService") { it.stop() } })
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

        private val headlessLatch = java.util.concurrent.CountDownLatch(1)

        private val headlessShutdownStarted =
            java.util.concurrent.atomic
                .AtomicBoolean(false)

        /**
         * The daemon counterpart of [exitCrossPasteApplication]: runs the exit
         * hooks around [shutdownAllServices] and releases the lock. Idempotent,
         * and each step is guarded, so the SIGTERM hook and the startup-failure
         * path cannot double-clean and one failing step cannot skip the rest.
         */
        private fun headlessShutdown() {
            if (!headlessShutdownStarted.compareAndSet(false, true)) {
                return
            }
            val koin = koinApplication.koin
            val appExitService = runCatching { koin.get<AppExitService>() }.getOrNull()
            appExitService?.beforeExitList?.forEach { hook ->
                runCatching { hook.invoke() }
                    .onFailure { e -> logger.error(e) { "beforeExit hook failed" } }
            }
            runCatching { runBlocking { shutdownAllServices() } }
                .onFailure { e -> logger.error(e) { "shutdownAllServices failed" } }
            appExitService?.beforeReleaseLockList?.forEach { hook ->
                runCatching { hook.invoke() }
                    .onFailure { e -> logger.error(e) { "beforeReleaseLock hook failed" } }
            }
            runCatching { koin.get<AppLock>().releaseLock() }
                .onFailure { e -> logger.error(e) { "releaseLock failed" } }
        }

        private fun registerHeadlessShutdownHook() {
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    logger.info { "Shutdown signal received" }
                    headlessShutdown()
                    headlessLatch.countDown()
                },
            )
        }

        private fun runHeadless() {
            logger.info { "Running in headless mode" }
            headlessLatch.await()
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
