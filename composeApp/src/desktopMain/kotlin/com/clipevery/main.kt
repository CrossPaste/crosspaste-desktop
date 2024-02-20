package com.clipevery

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.clipevery.app.AppEnv
import com.clipevery.app.AppFileType
import com.clipevery.app.AppUI
import com.clipevery.clip.ClipboardService
import com.clipevery.listen.GlobalListener
import com.clipevery.log.initLogger
import com.clipevery.net.ClipBonjourService
import com.clipevery.net.ClipClient
import com.clipevery.net.ClipServer
import com.clipevery.path.DesktopPathProvider
import com.clipevery.platform.currentPlatform
import com.clipevery.ui.getTrayMouseAdapter
import com.clipevery.utils.QRCodeGenerator
import com.clipevery.utils.getPreferredWindowSize
import com.clipevery.utils.ioDispatcher
import dev.datlag.kcef.KCEF
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.KoinApplication
import kotlin.io.path.pathString
import kotlin.math.max

fun initInject(koinApplication: KoinApplication) {
    koinApplication.koin.get<GlobalListener>()
    koinApplication.koin.get<QRCodeGenerator>()
    koinApplication.koin.get<ClipServer>()
    koinApplication.koin.get<ClipClient>()
    koinApplication.koin.get<ClipboardService>()
    koinApplication.koin.get<ClipBonjourService>()
}

fun exitClipEveryApplication(exitApplication: () -> Unit) {
    val koinApplication = Dependencies.koinApplication
    koinApplication.koin.get<ClipBonjourService>().unregisterService()
    koinApplication.koin.get<ClipServer>().stop()
    exitApplication()
}

fun main(args: Array<String>) {
    val logLevel = if (args.isNotEmpty()) {
        args[0]
    } else {
        "info"
    }

    Dependencies.init(if (args.size >= 2) {
        AppEnv.valueOf(args[1])
    } else {
        AppEnv.PRODUCTION
    })

    initLogger(DesktopPathProvider.resolve("clipevery.log", AppFileType.LOG).pathString, logLevel)
    val logger = KotlinLogging.logger {}

    logger.info { "Starting Clipevery" }

    val koinApplication = Dependencies.koinApplication

    initInject(koinApplication)

    val clipboardService = koinApplication.koin.get<ClipboardService>()
    clipboardService.start()

    application {
        val ioScope = rememberCoroutineScope { ioDispatcher }

        var restartRequired by remember { mutableStateOf(false) }
        var downloading by remember { mutableStateOf(0F) }
        var initialized by remember { mutableStateOf(false) }

        val appUI = koinApplication.koin.get<AppUI>()

        val trayIcon = if (currentPlatform().isMacos()) {
            painterResource("clipevery_mac_tray.png")
        } else {
            painterResource("clipevery_icon.png")
        }

        val windowState = rememberWindowState(
            placement = WindowPlacement.Floating,
            position = WindowPosition.PlatformDefault,
            size = getPreferredWindowSize(appUI)
        )

        Tray(
            icon = trayIcon,
            mouseListener = getTrayMouseAdapter(windowState) {
                appUI.showWindow = !appUI.showWindow
            },
        )

        val exitApplication: () -> Unit = {
            appUI.showWindow = false
            ioScope.launch {
                exitClipEveryApplication { exitApplication() }
            }
        }

        Window(
            onCloseRequest = exitApplication,
            visible = appUI.showWindow,
            state = windowState,
            title = "Clipevery",
            icon = painterResource("clipevery_icon.png"),
            alwaysOnTop = true,
            undecorated = true,
            transparent = true,
            resizable = false
        ) {

            LaunchedEffect(Unit) {
                window.addWindowFocusListener(object : java.awt.event.WindowFocusListener {
                    override fun windowGainedFocus(e: java.awt.event.WindowEvent?) {
                        appUI.showWindow = true
                    }

                    override fun windowLostFocus(e: java.awt.event.WindowEvent?) {
                        appUI.showWindow = false
                    }
                })



                val kcefBundleDir = DesktopPathProvider.resolve("kcef-bundle", AppFileType.KCEF).toFile()
                val kcefCacheDir = DesktopPathProvider.resolve("kcef-cache", AppFileType.KCEF).toFile()

                withContext(Dispatchers.IO) {
                    KCEF.init(builder = {
                        installDir(kcefBundleDir)
                        progress {
                            onDownloading {
                                downloading = max(it, 0F)
                            }
                            onInitialized {
                                initialized = true
                            }
                        }
                        settings {
                            cachePath = kcefCacheDir.absolutePath
                        }
                    }, onError = {
                        it?.printStackTrace()
                    }, onRestartRequired = {
                        restartRequired = true
                    })
                }
            }
            ClipeveryApp(
                koinApplication,
                hideWindow = { appUI.showWindow = false },
                exitApplication = exitApplication
            )
        }
    }
}
