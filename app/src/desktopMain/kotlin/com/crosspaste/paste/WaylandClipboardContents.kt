package com.crosspaste.paste

import com.crosspaste.paste.plugin.type.DesktopRtfTypePlugin.Companion.RTF_DATA_FLAVOR
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import javax.imageio.ImageIO

/**
 * Turns one of our clipboard writes into the MIME types a native Wayland
 * clipboard offer carries, the reverse of [WaylandClipboardSnapshot]: AWT is
 * not involved in a data-control write, so this does by hand what
 * `XDataTransferer` does when it exports flavors as X11 targets.
 *
 * Each payload is serialized on first request and then reused, so a paste that
 * only wants text never encodes the image (whose decoding is deferred too), and
 * single-use stream values (RTF) survive repeated requests.
 */
object WaylandClipboardContents {

    /**
     * UTF-8 text MIME types. Not the ICCCM `STRING`/`TEXT` targets: they mean Latin-1,
     * no Wayland client asks for them, and the compositor would hand X clients our
     * UTF-8 bytes under those names unconverted.
     */
    private val TEXT_MIME_TYPES = listOf("text/plain;charset=utf-8", "UTF8_STRING", "text/plain")

    private val RTF_MIME_TYPES = listOf("text/rtf", "application/rtf")

    private const val URI_LIST = "text/uri-list"

    private const val GNOME_COPIED_FILES = "x-special/gnome-copied-files"

    fun create(transferable: Transferable): Map<String, () -> ByteArray?> {
        val contents = LinkedHashMap<String, () -> ByteArray?>()

        fun offer(
            mimeTypes: List<String>,
            produce: () -> ByteArray?,
        ) {
            val once = lazy { produce() }
            mimeTypes.forEach { contents.putIfAbsent(it, once::value) }
        }

        fun valueOf(flavor: DataFlavor): Any? =
            if (transferable.isDataFlavorSupported(flavor)) {
                transferable.getTransferData(flavor).takeUnless { it === NoneTransferData }
            } else {
                null
            }

        // Files first, as on read: every other representation describes them.
        (valueOf(DataFlavor.javaFileListFlavor) as? List<*>)
            ?.filterIsInstance<File>()
            ?.takeIf { it.isNotEmpty() }
            ?.let { files ->
                val uris = files.map { it.toURI().toASCIIString().replaceFirst("file:/", "file:///") }
                offer(listOf(URI_LIST)) { uris.joinToString("\r\n", postfix = "\r\n").encodeToByteArray() }
                offer(listOf(GNOME_COPIED_FILES)) { (listOf("copy") + uris).joinToString("\n").encodeToByteArray() }
            }

        if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
            offer(listOf("image/png")) { (valueOf(DataFlavor.imageFlavor) as? Image)?.let(::encodePng) }
        }

        (valueOf(DataFlavor.allHtmlFlavor) as? String)?.let { html ->
            offer(listOf("text/html")) { html.encodeToByteArray() }
        }

        if (transferable.isDataFlavorSupported(RTF_DATA_FLAVOR)) {
            offer(RTF_MIME_TYPES) { (valueOf(RTF_DATA_FLAVOR) as? InputStream)?.use { it.readBytes() } }
        }

        (valueOf(DataFlavor.stringFlavor) as? String)?.let { text ->
            offer(TEXT_MIME_TYPES) { text.encodeToByteArray() }
        }

        // Only when there is something to paste: a bare marker must not empty the clipboard.
        if (contents.isNotEmpty()) {
            offer(listOf(PasteDataFlavors.CROSSPASTE_ORIGIN_MIME)) {
                PasteDataFlavors.CROSSPASTE_ORIGIN_MARKER.encodeToByteArray()
            }
        }
        return contents
    }

    private fun encodePng(image: Image): ByteArray? {
        val buffered =
            image as? BufferedImage
                ?: BufferedImage(
                    image.getWidth(null).takeIf { it > 0 } ?: return null,
                    image.getHeight(null).takeIf { it > 0 } ?: return null,
                    BufferedImage.TYPE_INT_ARGB,
                ).also { target ->
                    val graphics = target.createGraphics()
                    try {
                        graphics.drawImage(image, 0, 0, null)
                    } finally {
                        graphics.dispose()
                    }
                }
        val output = ByteArrayOutputStream()
        return if (ImageIO.write(buffered, "png", output)) output.toByteArray() else null
    }
}
