package com.clipevery

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.clipevery.config.ConfigManager
import com.clipevery.config.FileType
import com.clipevery.encrypt.CreateSignalProtocolState
import com.clipevery.encrypt.SignalProtocol
import com.clipevery.encrypt.getSignalProtocolFactory
import com.clipevery.log.initLogger
import com.clipevery.model.AppConfig
import com.clipevery.model.AppInfo
import com.clipevery.net.ClipServer
import com.clipevery.net.DesktopClipServer
import com.clipevery.path.PathProvider
import com.clipevery.path.getPathProvider
import com.clipevery.platform.currentPlatform
import com.clipevery.presist.DesktopOneFilePersist
import com.clipevery.presist.FilePersist
import com.clipevery.presist.OneFilePersist
import com.clipevery.ui.getTrayMouseAdapter
import com.clipevery.utils.DesktopQRCodeGenerator
import com.clipevery.utils.QRCodeGenerator
import com.clipevery.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.geom.RoundRectangle2D
import java.nio.file.Path
import kotlin.io.path.pathString


val height = 720.dp
val width = 440.dp


fun main() = application {
    val pathProvider = getPathProvider()
    initLogger(pathProvider.resolveLog("clipevery.log").pathString)
    val logger = KotlinLogging.logger {}

    logger.info { "Starting Clipevery" }

    val ioScope = rememberCoroutineScope { ioDispatcher }

    var showWindow by remember { mutableStateOf(false) }

    val appInfo = getAppInfoFactory().createAppInfo()

    val dependencies = remember {
        getDependencies(appInfo, ioScope)
    }

    val trayIcon = if(currentPlatform().isMacos()) {
        painterResource("clipevery_mac_tray.png")
    } else {
        painterResource("clipevery_icon.png")
    }

    val windowState = rememberWindowState(
        placement = WindowPlacement.Floating,
        position = WindowPosition.PlatformDefault,
        size = getPreferredWindowSize(width, height)
    )

    Tray(icon = trayIcon,
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
        resizable = false
    ) {

        LaunchedEffect(Unit) {
            window.addComponentListener(object : java.awt.event.ComponentAdapter() {
                override fun componentResized(e: java.awt.event.ComponentEvent?) {
                    applyRoundedCorners(window)
                }
            })
            window.addWindowFocusListener(object : java.awt.event.WindowFocusListener {
                override fun windowGainedFocus(e: java.awt.event.WindowEvent?) {
                    showWindow = true
                }

                override fun windowLostFocus(e: java.awt.event.WindowEvent?) {
                    showWindow = false
                }
            })
        }

        ClipeveryApp(dependencies)
    }
}

fun applyRoundedCorners(window: ComposeWindow) {
    val radius = 15.0
    val shape = RoundRectangle2D.Double(0.0, 0.0, window.width.toDouble(), window.height.toDouble(), radius, radius)
    window.shape = shape
}

private fun getPreferredWindowSize(desiredWidth: Dp, desiredHeight: Dp): DpSize {
    val screenSize: Dimension = Toolkit.getDefaultToolkit().screenSize
    val preferredWidth: Dp = (screenSize.width.dp * 0.8f)
    val preferredHeight: Dp = (screenSize.height.dp * 0.8f)
    val width: Dp = if (desiredWidth < preferredWidth) desiredWidth else preferredWidth
    val height: Dp = if (desiredHeight < preferredHeight) desiredHeight else preferredHeight
    return DpSize(width, height)
}

private fun getDependencies(
    appInfo: AppInfo,
    ioScope: CoroutineScope
) = object : Dependencies() {


    override val filePersist: FilePersist = object : FilePersist {
        override val pathProvider: PathProvider = getPathProvider()

        override fun createOneFilePersist(path: Path): OneFilePersist {
            return DesktopOneFilePersist(path)
        }
    }

    override val configManager: ConfigManager = object : ConfigManager(ioScope) {

        val configFilePersist = filePersist.getPersist("appConfig.json", FileType.USER)

        override fun loadConfig(): AppConfig? {
            return configFilePersist.read(AppConfig::class)
        }

        override fun saveConfigImpl(config: AppConfig) {
            configFilePersist.save(config)
        }
    }.initConfig()

    override val signalProtocol: SignalProtocol = run {
        val signalProtocolFactory = getSignalProtocolFactory(appInfo)
        val createSignalProtocol = signalProtocolFactory.createSignalProtocol()

        val state = createSignalProtocol.state
        if (state != CreateSignalProtocolState.EXISTING && configManager.config.bindingState) {
            configManager.updateBindingState(false)
        }

        createSignalProtocol.signalProtocol
    }

    override val clipServer: ClipServer = DesktopClipServer(signalProtocol).start()

    override val qrCodeGenerator: QRCodeGenerator = DesktopQRCodeGenerator(clipServer)


}

//@Preview
//@Composable
//fun AppDesktopPreview() {
//    ClipeveryApp(clipeveryAppState)
//}