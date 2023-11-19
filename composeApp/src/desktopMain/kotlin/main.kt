import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.clipevery.ClipeveryApp
import com.clipevery.getClipboard
import org.jetbrains.skia.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.InputStream
import java.util.function.Consumer
import kotlin.system.exitProcess


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

    val imageBitmap = loadIconFromResources("clipevery_icon.png")


    Tray(
        icon = imageBitmap,
        menu = {
            Item(
                "Exit",
                onClick = { exitProcess(1) }
            )
        }
    )

    Window(onCloseRequest = ::exitApplication) {
        ClipeveryApp(clipboard, copyText)
    }
}

fun loadIconFromResources(resourceName: String): BitmapPainter {
    val resourceStream: InputStream? = Thread.currentThread().contextClassLoader.getResourceAsStream(resourceName)
    resourceStream?.let {
        val image = Image.makeFromEncoded(it.readAllBytes())
        return BitmapPainter(image.toComposeImageBitmap())
    }
    throw IllegalArgumentException("Resource not found: $resourceName")
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