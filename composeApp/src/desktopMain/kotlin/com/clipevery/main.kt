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
import com.clipevery.app.AppFileType
import com.clipevery.clip.ClipboardService
import com.clipevery.listen.GlobalListener
import com.clipevery.log.initLogger
import com.clipevery.net.ClipBonjourService
import com.clipevery.net.ClipClient
import com.clipevery.net.ClipServer
import com.clipevery.path.getPathProvider
import com.clipevery.platform.currentPlatform
import com.clipevery.ui.getTrayMouseAdapter
import com.clipevery.utils.QRCodeGenerator
import com.clipevery.utils.getPreferredWindowSize
import com.clipevery.utils.initAppUI
import com.clipevery.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.launch
import org.koin.core.KoinApplication
import kotlin.io.path.pathString


val appUI = initAppUI()


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

fun main() = application {
    val pathProvider = getPathProvider()
    initLogger(pathProvider.resolve("clipevery.log", AppFileType.LOG).pathString)
    val logger = KotlinLogging.logger {}

    logger.info { "Starting Clipevery" }

    val ioScope = rememberCoroutineScope { ioDispatcher }

    var showWindow by remember { mutableStateOf(false) }

    val koinApplication = Dependencies.koinApplication

    initInject(koinApplication)

    val trayIcon = if(currentPlatform().isMacos()) {
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
        mouseListener = getTrayMouseAdapter(windowState) { showWindow = !showWindow },
    )

    val exitApplication: () -> Unit = {
        showWindow = false
        ioScope.launch {
            exitClipEveryApplication { exitApplication() }
        }
    }

    Window(
        onCloseRequest = exitApplication,
        visible = showWindow,
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
                    showWindow = true
                }

                override fun windowLostFocus(e: java.awt.event.WindowEvent?) {
                    showWindow = false
                }
            })
        }
        ClipeveryApp(koinApplication,
            hideWindow = { showWindow = false },
            exitApplication = exitApplication)
    }
}
