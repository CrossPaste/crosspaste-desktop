import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.clipevery.ClipeveryApp
import com.clipevery.getClipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.util.function.Consumer


fun main() = application {
    val copyText = remember { mutableStateOf("Hello World!") }
    val consumer = Consumer<Transferable> {
        if (it.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            try {
                copyText.value = it.getTransferData(DataFlavor.stringFlavor).toString()
                println(it.getTransferData(DataFlavor.stringFlavor))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    val clipboard = getClipboard(consumer)
    clipboard.start()
    Window(onCloseRequest = ::exitApplication) {
        ClipeveryApp(clipboard, copyText)
    }
}

@Preview
@Composable
fun AppDesktopPreview() {
    val copyText = remember { mutableStateOf("Hello World!") }
    val consumer = Consumer<Transferable> {
        if (it.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            try {
                copyText.value = it.getTransferData(DataFlavor.stringFlavor).toString()
                println(it.getTransferData(DataFlavor.stringFlavor))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    val clipboard = getClipboard(consumer)
    ClipeveryApp(clipboard, copyText)
}