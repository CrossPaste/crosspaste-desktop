import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.clipevery.AppConfig
import com.clipevery.ClipeveryApp
import com.clipevery.Dependencies
import com.clipevery.config.ConfigManager
import com.clipevery.config.FileType
import com.clipevery.log.initLogger
import com.clipevery.net.ClipServer
import com.clipevery.path.PathProvider
import com.clipevery.path.getPathProvider
import com.clipevery.presist.DesktopOneFilePersist
import com.clipevery.presist.FilePersist
import com.clipevery.presist.OneFilePersist
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

    val dependencies = remember {
        getDependencies(ioScope)
    }

    Tray(icon = painterResource("clipevery_icon.png"),
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
    ioScope: CoroutineScope
) = object : Dependencies() {

    val logger = KotlinLogging.logger {}

    override val clipServer: ClipServer = object : ClipServer {
    }

    override val filePersist: FilePersist = object : FilePersist {
        override val pathProvider: PathProvider = run {
            val innerPathProvider = getPathProvider()
            logger.info { "userPath ${innerPathProvider.resolveUser(null)}" }
            logger.info { "appPath ${innerPathProvider.resolveApp(null)}" }
            logger.info { "logPath ${innerPathProvider.resolveLog(null)}" }
            getPathProvider()
        }

        override fun createOneFilePersist(path: Path): OneFilePersist {
            return DesktopOneFilePersist(path)
        }
    }

    override val configManager: ConfigManager = object : ConfigManager(ioScope) {

        val configFilePersist = filePersist.getPersist("appConfig.json", FileType.USER)

        override fun loadConfig(): AppConfig? {
            return configFilePersist.readAs(AppConfig::class)
        }

        override fun saveConfigImpl(config: AppConfig) {
            configFilePersist.save(config)
        }
    }.initConfig()
}

//@Preview
//@Composable
//fun AppDesktopPreview() {
//    ClipeveryApp(clipeveryAppState)
//}