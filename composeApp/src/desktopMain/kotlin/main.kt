import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.clipevery.model.AppConfig
import com.clipevery.model.AppInfo
import com.clipevery.ClipeveryApp
import com.clipevery.Dependencies
import com.clipevery.config.ConfigManager
import com.clipevery.config.FileType
import com.clipevery.encrypt.CreateSignalProtocolState
import com.clipevery.encrypt.SignalProtocol
import com.clipevery.encrypt.getSignalProtocolFactory
import com.clipevery.getAppInfoFactory
import com.clipevery.log.initLogger
import com.clipevery.net.ClipServer
import com.clipevery.net.DesktopClipServer
import com.clipevery.path.PathProvider
import com.clipevery.path.getPathProvider
import com.clipevery.platform.currentPlatform
import com.clipevery.presist.DesktopOneFilePersist
import com.clipevery.presist.FilePersist
import com.clipevery.presist.OneFilePersist
import com.clipevery.utils.DesktopQRCodeGenerator
import com.clipevery.utils.QRCodeGenerator
import com.clipevery.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.system.exitProcess


fun main() = application {
    val pathProvider = getPathProvider()
    initLogger(pathProvider.resolveLog("clipevery.log").pathString)
    val logger = KotlinLogging.logger {}

    logger.info { "Starting Clipevery" }

    val ioScope = rememberCoroutineScope { ioDispatcher }

    val appInfo = getAppInfoFactory().createAppInfo()

    val dependencies = remember {
        getDependencies(appInfo, ioScope)
    }

    val trayIcon = if(currentPlatform().isMacos()) {
        painterResource("clipevery_mac_tray.png")
    } else {
        painterResource("clipevery_icon.png")
    }

    Tray(icon = trayIcon,
        menu = {
            Item(
                "Exit",
                onClick = { exitProcess(1) }
            )
        }
    )

    Window(onCloseRequest = ::exitApplication,
        title = "Clipevery",
        icon = painterResource("clipevery_icon.png"),
        undecorated = true,
        resizable = true) {
        ClipeveryApp(dependencies)
    }
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