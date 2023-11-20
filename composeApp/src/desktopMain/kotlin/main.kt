import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.clipevery.AppConfig
import com.clipevery.ClipeveryApp
import com.clipevery.Dependencies
import com.clipevery.net.ClipServer
import com.clipevery.net.ConfigManager
import com.clipevery.utils.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlin.system.exitProcess


fun main() = application {
    val ioScope = rememberCoroutineScope { ioDispatcher }
    val bingingState = remember { mutableStateOf(false) }

    val dependencies = remember {
        getDependencies(ioScope, bingingState)
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
    ioScope: CoroutineScope,
    bingingState: MutableState<Boolean>
) = object : Dependencies() {
    override val clipServer: ClipServer = object : ClipServer {
    }

    override val configManager: ConfigManager = object : ConfigManager {
        override val config: AppConfig = AppConfig(false) // todo: read from disk config file
    }
}

//@Preview
//@Composable
//fun AppDesktopPreview() {
//    ClipeveryApp(clipeveryAppState)
//}