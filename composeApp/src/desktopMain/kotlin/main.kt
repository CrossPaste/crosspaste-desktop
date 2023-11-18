import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.clipevery.ClipeveryApp
import com.clipevery.windows.WindowsClipboard

fun main() = application {
    val copyText = remember { mutableStateOf("Hello World!") }
    val windowsClipboard = WindowsClipboard(copyText)
    windowsClipboard.start()
    Window(onCloseRequest = ::exitApplication) {
        ClipeveryApp(windowsClipboard, copyText)
    }
}

@Preview
@Composable
fun AppDesktopPreview() {
    val copyText = remember { mutableStateOf("Hello World!") }
    val windowsClipboard = WindowsClipboard(copyText)
    ClipeveryApp(windowsClipboard, copyText)
}