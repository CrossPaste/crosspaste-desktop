package com.crosspaste

import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.application
import com.crosspaste.app.AppExitService
import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppLock
import com.crosspaste.app.AppStartUpService
import com.crosspaste.app.AppUpdateService
import com.crosspaste.app.DesktopAppLaunchState
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.ExitMode
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.crosspaste
import com.crosspaste.app.generated.resources.crosspaste_mac
import com.crosspaste.clean.CleanPasteScheduler
import com.crosspaste.config.ConfigManager
import com.crosspaste.config.DefaultConfigManager
import com.crosspaste.listener.GlobalListener
import com.crosspaste.log.DesktopCrossPasteLogger
import com.crosspaste.net.PasteBonjourService
import com.crosspaste.net.PasteClient
import com.crosspaste.net.Server
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.path.DesktopAppPathProvider
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.getPlatform
import com.crosspaste.presist.FilePersist
import com.crosspaste.rendering.RenderingService
import com.crosspaste.sync.QRCodeGenerator
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.CrossPasteMainWindow
import com.crosspaste.ui.CrossPasteSearchWindow
import com.crosspaste.ui.GrantAccessibilityPermissionsWindow
import com.crosspaste.ui.LinuxTrayView
import com.crosspaste.ui.LocalExitApplication
import com.crosspaste.ui.MacTrayView
import com.crosspaste.ui.WindowsTrayView
import com.crosspaste.ui.base.PasteContextMenuRepresentation
import com.crosspaste.utils.DesktopDeviceUtils
import com.crosspaste.utils.DesktopLocaleUtils
import com.crosspaste.utils.getAppEnvUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.koin.core.KoinApplication
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import kotlin.system.exitProcess

class CrossPaste {

    companion object {

        private val appEnvUtils = getAppEnvUtils()

        private val appEnv = appEnvUtils.getCurrentAppEnv()

        private val appPathProvider = DesktopAppPathProvider

        private val deviceUtils = DesktopDeviceUtils

        private val localeUtils = DesktopLocaleUtils

        private val configManager =
            DefaultConfigManager(
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
            DesktopCrossPasteModule(
                appEnv,
                appPathProvider,
                configManager,
                crossPasteLogger,
                logger,
            )

        val koinApplication: KoinApplication = module.initKoinApplication()

        @Throws(Exception::class)
        private fun startApplication() {
            runCatching {
                val koin = koinApplication.koin
                val appLaunchState = koin.get<DesktopAppLaunchState>()
                if (appLaunchState.acquireLock) {
                    val configManager = koin.get<ConfigManager>()
                    val notificationManager = koin.get<NotificationManager>()
                    configManager.notificationManager = notificationManager
                    if (configManager.config.enablePasteboardListening) {
                        koin.get<PasteboardService>().start()
                    }
                    koin.get<QRCodeGenerator>()
                    koin.get<Server>().start()
                    koin.get<PasteClient>()
                    // bonjour service should be registered after paste server started
                    // only server started, bonjour service can get the port
                    koin.get<PasteBonjourService>().registerService()
                    koin.get<CleanPasteScheduler>().start()
                    koin.get<AppStartUpService>().followConfig()
                    koin.get<AppUpdateService>().start()
                    koin.get<RenderingService<String>>(named("htmlRendering")).start()
                    koin.get<RenderingService<String>>(named("rtfRendering")).start()
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
                appWindowManager.setShowMainWindow(false)
            }
            exitApplication()
        }

        private suspend fun shutdownAllServices() {
            supervisorScope {
                val jobs =
                    listOf(
                        async {
                            stopService<AppUpdateService>("AppUpdateService") { it.stop() }
                        },
                        async {
                            stopService<RenderingService<String>>(
                                qualifier = named("htmlRendering"),
                                serviceName = "RenderingService",
                            ) { it.stop() }
                        },
                        async {
                            stopService<RenderingService<String>>(
                                qualifier = named("rtfRendering"),
                                serviceName = "RenderingService",
                            ) { it.stop() }
                        },
                        async { stopService<PasteboardService>("PasteboardService") { it.stop() } },
                        async { stopService<PasteBonjourService>("PasteBonjourService") { it.unregisterService() } },
                        async { stopService<Server>("PasteServer") { it.stop() } },
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

        @JvmStatic
        fun main(args: Array<String>) {
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                logger.error(throwable) { "Uncaught exception in thread: $thread" }
            }

            logger.info { "Starting CrossPaste" }
            startApplication()
            logger.info { "CrossPaste started" }

            val appLaunchState = koinApplication.koin.get<DesktopAppLaunchState>()
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
                        appWindowManager.setShowMainWindow(false)
                    }
                    exiting = true
                    appWindowManager.setShowSearchWindow(false)
                    ioScope.launch {
                        exitCrossPasteApplication(mode, ioScope) { exitApplication() }
                    }
                }

                val contextMenuRepresentation = remember { PasteContextMenuRepresentation() }

                CompositionLocalProvider(
                    LocalExitApplication provides exitApplication,
                    LocalContextMenuRepresentation provides contextMenuRepresentation,
                ) {
                    val windowIcon: Painter? =
                        if (isMacos) {
                            painterResource(Res.drawable.crosspaste_mac)
                        } else if (isWindows || isLinux) {
                            painterResource(Res.drawable.crosspaste)
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
