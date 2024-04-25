package com.clipevery

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.clipevery.app.AppFileType
import com.clipevery.app.AppUI
import com.clipevery.clean.CleanClipScheduler
import com.clipevery.clip.ChromeService
import com.clipevery.clip.ClipSearchService
import com.clipevery.clip.ClipboardService
import com.clipevery.listen.GlobalListener
import com.clipevery.log.initLogger
import com.clipevery.net.ClipBonjourService
import com.clipevery.net.ClipClient
import com.clipevery.net.ClipServer
import com.clipevery.path.DesktopPathProvider
import com.clipevery.platform.currentPlatform
import com.clipevery.ui.getTrayMouseAdapter
import com.clipevery.utils.FileUtils
import com.clipevery.utils.QRCodeGenerator
import com.clipevery.utils.getPreferredWindowSize
import com.clipevery.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import org.koin.core.KoinApplication
import kotlin.io.path.pathString

class Clipevery {

    companion object {

        fun initInject(koinApplication: KoinApplication) {
            koinApplication.koin.get<FileUtils>()
            koinApplication.koin.get<GlobalListener>()
            koinApplication.koin.get<QRCodeGenerator>()
            koinApplication.koin.get<ClipServer>()
            koinApplication.koin.get<ClipClient>()
            koinApplication.koin.get<ClipboardService>()
            koinApplication.koin.get<ClipBonjourService>()
            koinApplication.koin.get<CleanClipScheduler>()
        }

        fun exitClipEveryApplication(exitApplication: () -> Unit) {
            val koinApplication = Dependencies.koinApplication
            koinApplication.koin.get<ClipboardService>().stop()
            koinApplication.koin.get<ClipSearchService>().stop()
            koinApplication.koin.get<ClipBonjourService>().unregisterService()
            koinApplication.koin.get<ClipServer>().stop()
            koinApplication.koin.get<CleanClipScheduler>().stop()
            koinApplication.koin.get<ChromeService>().quit()
            exitApplication()
        }

        @JvmStatic
        fun main(args: Array<String>) {
            initLogger(DesktopPathProvider.resolve("clipevery.log", AppFileType.LOG).pathString)
            val logger = KotlinLogging.logger {}

            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                logger.error(throwable) { "Uncaught exception in thread: $thread" }
            }

            logger.info { "Starting Clipevery" }
            try {
                val koinApplication = Dependencies.koinApplication

                initInject(koinApplication)

                val clipboardService = koinApplication.koin.get<ClipboardService>()
                clipboardService.start()

                val cleanClipScheduler = koinApplication.koin.get<CleanClipScheduler>()
                cleanClipScheduler.start()
            } catch (throwable: Throwable) {
                logger.error(throwable) { "cant start clipevery" }
            }

            application {
                val ioScope = rememberCoroutineScope { ioDispatcher }

                val appUI = Dependencies.koinApplication.koin.get<AppUI>()

                val trayIcon =
                    if (currentPlatform().isMacos()) {
                        painterResource("clipevery_mac_tray.png")
                    } else {
                        painterResource("clipevery_icon.png")
                    }

                val windowState =
                    rememberWindowState(
                        placement = WindowPlacement.Floating,
                        position = WindowPosition.PlatformDefault,
                        size = getPreferredWindowSize(appUI),
                    )

                Tray(
                    icon = trayIcon,
                    mouseListener =
                        getTrayMouseAdapter(windowState) {
                            appUI.showMainWindow = !appUI.showMainWindow
                        },
                )

                val exitApplication: () -> Unit = {
                    appUI.showMainWindow = false
                    ioScope.launch(CoroutineName("ExitApplication")) {
                        exitClipEveryApplication { exitApplication() }
                    }
                }

                Window(
                    onCloseRequest = exitApplication,
                    visible = appUI.showMainWindow,
                    state = windowState,
                    title = "Clipevery",
                    icon = painterResource("clipevery_icon.png"),
                    alwaysOnTop = true,
                    undecorated = true,
                    transparent = true,
                    resizable = false,
                ) {
                    LaunchedEffect(Unit) {
                        window.addWindowFocusListener(
                            object : java.awt.event.WindowFocusListener {
                                override fun windowGainedFocus(e: java.awt.event.WindowEvent?) {
                                    appUI.showMainWindow = true
                                }

                                override fun windowLostFocus(e: java.awt.event.WindowEvent?) {
                                    appUI.showMainWindow = false
                                }
                            },
                        )
                    }
                    ClipeveryApp(
                        Dependencies.koinApplication,
                        hideWindow = { appUI.showMainWindow = false },
                        exitApplication = exitApplication,
                    )
                }
            }
        }
    }
}
