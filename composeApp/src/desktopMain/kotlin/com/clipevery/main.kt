package com.clipevery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
import com.clipevery.utils.getPreferredWindowSize
import com.clipevery.utils.initAppUI
import com.clipevery.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import java.awt.geom.RoundRectangle2D
import java.nio.file.Path
import kotlin.io.path.pathString


val appUI = initAppUI()

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
        size = getPreferredWindowSize(appUI)
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
        CustomWindowDecoration()
        ClipeveryApp(dependencies)
    }
}


@Composable
fun CustomWindowDecoration() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp),
        color = MaterialTheme.colors.background,
        shape = RoundedCornerShape(
            topStart = 10.dp,
            topEnd = 10.dp,
            bottomEnd = 0.dp,
            bottomStart = 0.dp
        )
    ) {
        Box(
            modifier = Modifier.background(Color.Black)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.6f),
                            Color.Transparent
                        ),
                        startY = 0.0f,
                        endY = 3.0f
                    )
                ),
        ) {
            // Custom title bar content
        }
    }
}

fun applyRoundedCorners(window: ComposeWindow) {
    val radius = 20.0
    val shape = RoundRectangle2D.Double(0.0, 0.0, window.width.toDouble(), window.height.toDouble(), radius, radius)
    window.shape = shape
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