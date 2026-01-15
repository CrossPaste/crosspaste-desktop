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
import com.crosspaste.app.ExitMode
import com.crosspaste.clean.CleanScheduler
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.db.DriverFactory
import com.crosspaste.listener.GlobalListener
import com.crosspaste.log.DesktopCrossPasteLogger
import com.crosspaste.net.PasteBonjourService
import com.crosspaste.net.PasteClient
import com.crosspaste.net.Server
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.GuidePasteDataService
import com.crosspaste.paste.PasteboardService
import com.crosspaste.path.DesktopAppPathProvider
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.DesktopPlatformProvider
import com.crosspaste.presist.FilePersist
import com.crosspaste.rendering.RenderingService
import com.crosspaste.sync.QRCodeGenerator
import com.crosspaste.sync.SyncManager
import com.crosspaste.task.TaskExecutor
import com.crosspaste.ui.AwtExceptionHandler
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
import org.koin.compose.koinInject
import org.koin.core.KoinApplication
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import kotlin.jvm.java
import kotlin.system.exitProcess

class CrossPaste {

    companion object {

        private val appEnvUtils = getAppEnvUtils()

        private val appEnv = appEnvUtils.getCurrentAppEnv()

        private val platform = DesktopPlatformProvider().getPlatform()

        private val appPathProvider = DesktopAppPathProvider(platform)

        private val deviceUtils = DesktopDeviceUtils(platform)

        private val localeUtils = DesktopLocaleUtils

        private val configManager =
            DesktopConfigManager(
                FilePersist.createOneFilePersist(
                    appPathProvider.resolve("appConfig.json", AppFileType.USER),
                ),
                deviceUtils,
                localeUtils,
            )

        private val crossPasteLogger =
            DesktopCrossPasteLogger(
                appPathProvider.resolve("crosspaste.log", AppFileType.LOG).toString(),
                configManager,
            )

        private val logger: KLogger = KotlinLogging.logger {}

        private val module =
            DesktopModule(
                appEnv,
                appPathProvider,
                configManager,
                crossPasteLogger,
                deviceUtils,
                logger,
                platform,
            )

        val koinApplication: KoinApplication = module.initKoinApplication()

        @Throws(Exception::class)
        private suspend fun startApplication() {
            runCatching {
                if (appEnvUtils.isProduction()) {
                    System.setProperty("jna.library.path", appPathProvider.pasteAppExePath.toString())
                }

                val koin = koinApplication.koin
                val appLaunchState = koin.get<AppLaunchState>()
                if (appLaunchState.acquireLock) {
                    val configManager = koin.get<DesktopConfigManager>()
                    val notificationManager = koin.get<NotificationManager>()
                    configManager.notificationManager = notificationManager
                    if (configManager.getCurrentConfig().enablePasteboardListening) {
                        koin.get<PasteboardService>().start()
                    }
                    koin.get<QRCodeGenerator>()
                    koin.get<SyncManager>().start()
                    koin.get<Server>().start()
                    koin.get<PasteClient>()
                    // bonjour service should be registered after paste server started
                    // only server started, bonjour service can get the port
                    koin.get<PasteBonjourService>()
                    koin.get<CleanScheduler>().start()
                    koin.get<AppStartUpService>().followConfig()
                    koin.get<AppUpdateService>().start()
                    koin.get<GuidePasteDataService>().initData()
                    koin.get<DesktopAppWindowManager>().startWindowService()

                    FileKit.init(appId = AppName)

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
                    exitProcess(0)
                }
            }.onFailure { e ->
                logger.error(e) { "cant start crosspaste" }
                exitProcess(0)
            }
        }

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
            if (exitMode == ExitMode.MIGRATION) {
                val appWindowManager = koin.get<DesktopAppWindowManager>()
                appWindowManager.hideMainWindow()
            }
            exitApplication()
        }

        private suspend fun shutdownAllServices() {
            withTimeoutOrNull(5000L) {
                supervisorScope {
                    val jobs =
                        listOf(
                            async {
                                stopService<AppUpdateService>("AppUpdateService") { it.stop() }
                            },
                            async {
                                stopService<TaskExecutor>("TaskExecutor") { it.shutdown() }
                            },
                            async {
                                stopService<RenderingService<String>>(
                                    qualifier = named("urlRendering"),
                                    serviceName = "RenderingService",
                                ) { it.stop() }
                            },
                            async { stopService<PasteboardService>("PasteboardService") { it.stop() } },
                            async { stopService<PasteBonjourService>("PasteBonjourService") { it.close() } },
                            async { stopService<Server>("PasteServer") { it.stop() } },
                            async { stopService<SyncManager>("SyncManager") { it.stop() } },
                            async { stopService<CleanScheduler>("CleanPasteScheduler") { it.stop() } },
                            async {
                                stopService<DesktopAppWindowManager>("DesktopAppWindowManager") {
                                    it.stopWindowService()
                                }
                            },
                            async { stopService<GlobalListener>("GlobalListener") { it.stop() } },
                            async { stopService<UserDataPathProvider>("UserDataPathProvider") { it.cleanTemp() } },
                        )

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

        @OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
        @JvmStatic
        fun main(args: Array<String>) {
            System.setProperty("sun.awt.exception.handler", AwtExceptionHandler::class.java.name)

            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                logger.error(throwable) { "Uncaught exception in thread: $thread" }
            }

            logger.info { "Starting CrossPaste" }
            runBlocking { startApplication() }
            logger.info { "CrossPaste started" }

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
                    if (globalListener.isRegistered()) {
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
