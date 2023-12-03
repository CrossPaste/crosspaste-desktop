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
import com.clipevery.clip.ClipboardService
import com.clipevery.clip.DesktopTransferableConsumer
import com.clipevery.clip.TransferableConsumer
import com.clipevery.clip.getDesktopClipboardService
import com.clipevery.config.ConfigManager
import com.clipevery.config.DefaultConfigManager
import com.clipevery.config.FileType
import com.clipevery.device.DesktopDeviceInfoFactory
import com.clipevery.device.DeviceInfoFactory
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.i18n.GlobalCopywriterImpl
import com.clipevery.listen.GlobalListener
import com.clipevery.log.initLogger
import com.clipevery.model.AppInfo
import com.clipevery.model.DesktopAppInfoFactory
import com.clipevery.net.ClipClient
import com.clipevery.net.ClipServer
import com.clipevery.net.DesktopClipClient
import com.clipevery.net.DesktopClipServer
import com.clipevery.path.getPathProvider
import com.clipevery.platform.currentPlatform
import com.clipevery.presist.FilePersist
import com.clipevery.presist.data.DesktopIdentityKeyStore
import com.clipevery.presist.data.DesktopPreKeyStore
import com.clipevery.presist.data.DesktopSessionStore
import com.clipevery.presist.data.DesktopSignedPreKeyStore
import com.clipevery.presist.data.DriverFactory
import com.clipevery.presist.data.createDatabase
import com.clipevery.presist.file.DesktopFilePersist
import com.clipevery.ui.DesktopThemeDetector
import com.clipevery.ui.ThemeDetector
import com.clipevery.ui.getTrayMouseAdapter
import com.clipevery.utils.DesktopQRCodeGenerator
import com.clipevery.utils.QRCodeGenerator
import com.clipevery.utils.getPreferredWindowSize
import com.clipevery.utils.initAppUI
import com.clipevery.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import org.koin.core.KoinApplication
import org.koin.core.context.GlobalContext.startKoin
import org.koin.dsl.module
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.PreKeyStore
import org.signal.libsignal.protocol.state.SessionStore
import org.signal.libsignal.protocol.state.SignedPreKeyStore
import kotlin.io.path.pathString


val appUI = initAppUI()

fun initKoinApplication(ioScope: CoroutineScope): KoinApplication {
    val appModule = module {
        single<CoroutineScope> { ioScope }
        single<AppInfo> { DesktopAppInfoFactory(get()).createAppInfo() }
        single<FilePersist> { DesktopFilePersist() }
        single<ConfigManager> { DefaultConfigManager(get()).initConfig() }
        single<ClipServer> { DesktopClipServer().start() }
        single<ClipClient> { DesktopClipClient() }
        single<DeviceInfoFactory> { DesktopDeviceInfoFactory() }
        single<QRCodeGenerator> { DesktopQRCodeGenerator(get(), get(), get()) }
        single<GlobalCopywriter> { GlobalCopywriterImpl(get()) }
        single<ClipboardService> { getDesktopClipboardService(get()) }
        single<TransferableConsumer> { DesktopTransferableConsumer() }
        single<GlobalListener> { GlobalListener() }
        single<DriverFactory> { DriverFactory() }
        single<ThemeDetector> { DesktopThemeDetector(get()) }
        single<IdentityKeyStore> { DesktopIdentityKeyStore(get(), get()) }
        single<PreKeyStore> { DesktopPreKeyStore(get()) }
        single<SessionStore> { DesktopSessionStore(get()) }
        single<SignedPreKeyStore> { DesktopSignedPreKeyStore(get()) }
        single<Database> { createDatabase(DriverFactory()) }
    }
    return startKoin {
        modules(appModule)
    }
}

fun initInject(koinApplication: KoinApplication) {
    koinApplication.koin.get<GlobalListener>()
    koinApplication.koin.get<QRCodeGenerator>()
    koinApplication.koin.get<ClipServer>()
    koinApplication.koin.get<ClipClient>()
    koinApplication.koin.get<ClipboardService>()
}

fun main() = application {
    val pathProvider = getPathProvider()
    initLogger(pathProvider.resolve("clipevery.log", FileType.LOG).pathString)
    val logger = KotlinLogging.logger {}

    logger.info { "Starting Clipevery" }

    val ioScope = rememberCoroutineScope { ioDispatcher }

    var showWindow by remember { mutableStateOf(false) }

    val koinApplication by remember { mutableStateOf(initKoinApplication(ioScope)) }

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

    Window(
        onCloseRequest = ::exitApplication,
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
            exitApplication = ::exitApplication)
    }
}
